package com.technoral.petkit.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Petkit işlemlerinin tek giriş noktası. Ekranlar doğrudan API ile değil
 * bu sınıfla konuşur.
 */
class PetkitRepository(val api: PetkitApi, val prefs: Prefs) {

    private val gunBicimi = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val saatBicimi = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))

    fun bugunKodu(): String = gunBicimi.format(Date())

    fun gunKodu(gunFarki: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, gunFarki)
        return gunBicimi.format(c.time)
    }

    fun gunEtiketi(gunKodu: String): String = try {
        val d = gunBicimi.parse(gunKodu)
        SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr", "TR")).format(d!!)
    } catch (e: Exception) {
        gunKodu
    }

    // ------------------------------------------------------------------ oturum

    suspend fun girisYap(
        kullanici: String,
        sifre: String,
        bolge: PetkitBolge,
        sifreyiSakla: Boolean = true
    ) {
        prefs.sunucuUrl = bolge.taban
        prefs.sifreyiSakla = sifreyiSakla
        api.girisYap(kullanici.trim(), sifre, bolge.kod)
    }

    fun cikisYap() {
        prefs.hepsiniTemizle()
    }

    // ------------------------------------------------------------------ cihazlar

    /**
     * Hesaba bağlı tüm cihazları getirir.
     *
     * İki ayrı uç nokta denenir. İkisi de hata verirse hatalar yutulmaz;
     * "cihaz yok" ile "istek reddedildi" ayırt edilebilsin diye birleştirilip
     * fırlatılır.
     */
    suspend fun cihazlar(): List<Cihaz> {
        val bulunanlar = LinkedHashMap<String, Cihaz>()
        val hatalar = mutableListOf<String>()

        // 1) Ana yol: cihaz listesi
        try {
            val sonuc = api.postYenilemeli("discovery/device_roster", mapOf("day" to bugunKodu()))
            cihazRosterAyikla(sonuc).forEach { bulunanlar[it.id] = it }
        } catch (e: Exception) {
            hatalar += "device_roster: ${e.message ?: e.javaClass.simpleName}"
        }

        // 2) Yedek yol: aile/grup listesi
        if (bulunanlar.isEmpty()) {
            try {
                val sonuc = api.postYenilemeli("group/family/list", emptyMap())
                aileListesiAyikla(sonuc).forEach { bulunanlar[it.id] = it }
            } catch (e: Exception) {
                hatalar += "family/list: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        if (bulunanlar.isEmpty() && hatalar.isNotEmpty()) {
            throw IllegalStateException(
                "Cihaz listesi alınamadı. " + hatalar.joinToString(" · ")
            )
        }
        return bulunanlar.values.toList()
    }

    private fun cihazRosterAyikla(sonuc: JsonElement): List<Cihaz> {
        val liste = when {
            sonuc is JsonObject && sonuc["devices"] != null -> sonuc["devices"].jsonArrayGuvenli()
            sonuc is JsonObject && sonuc["list"] != null -> sonuc["list"].jsonArrayGuvenli()
            sonuc is JsonObject && sonuc["result"] != null -> sonuc["result"].jsonArrayGuvenli()
            sonuc is JsonArray -> sonuc.toList()
            else -> emptyList()
        }
        return liste.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val veri = (o["data"] as? JsonObject) ?: o
            val tip = (o.metin("type") ?: veri.metin("type") ?: veri.metin("typeCode")
                ?: o.metin("deviceType") ?: veri.metin("deviceType") ?: "bilinmeyen")
                .lowercase()
            val id = veri.metin("id") ?: o.metin("deviceId") ?: o.metin("id")
                ?: return@mapNotNull null
            Cihaz(
                id = id,
                ad = veri.metin("name") ?: o.metin("deviceName") ?: CihazTipleri.etiket(tip),
                tip = tip,
                tipEtiketi = CihazTipleri.etiket(tip),
                seriNo = veri.metin("sn"),
                grupId = o.metin("groupId") ?: veri.metin("groupId"),
                ham = veri
            )
        }
    }

    private fun aileListesiAyikla(sonuc: JsonElement): List<Cihaz> {
        val cikti = mutableListOf<Cihaz>()
        val aileler = when (sonuc) {
            is JsonArray -> sonuc.toList()
            is JsonObject -> sonuc["list"].jsonArrayGuvenli().ifEmpty { listOf(sonuc) }
            else -> emptyList()
        }
        aileler.forEach { aile ->
            val a = aile as? JsonObject ?: return@forEach
            a["deviceList"].jsonArrayGuvenli().forEach inner@{ d ->
                val o = d as? JsonObject ?: return@inner
                val tip = (o.metin("deviceType") ?: o.metin("type") ?: return@inner).lowercase()
                val id = o.metin("deviceId") ?: o.metin("id") ?: return@inner
                cikti += Cihaz(
                    id = id,
                    ad = o.metin("deviceName") ?: o.metin("name") ?: CihazTipleri.etiket(tip),
                    tip = tip,
                    tipEtiketi = CihazTipleri.etiket(tip),
                    seriNo = o.metin("sn"),
                    grupId = a.metin("groupId") ?: a.metin("id"),
                    ham = o
                )
            }
        }
        return cikti
    }

    /** Cihaz ayrıntısı (durum + ayarlar + plan). */
    suspend fun detay(cihaz: Cihaz): JsonObject? {
        val sonuc = api.postYenilemeli(
            "${cihaz.tip}/device_detail",
            mapOf("id" to cihaz.id, "deviceId" to cihaz.id)
        )
        return sonuc as? JsonObject
    }

    // ------------------------------------------------------------------ besleme

    /**
     * Anında (elle) besleme. Çift hazneli modellerde iki hazne ayrı ayrı verilir.
     * Miktarlar porsiyon cinsindendir (varsayılan 1 porsiyon = 10 g).
     * Dönüş: iptal için kullanılabilecek besleme kimliği (varsa).
     */
    suspend fun elleBesle(cihaz: Cihaz, porsiyon1: Int, porsiyon2: Int): String? {
        val p = mutableMapOf(
            "deviceId" to cihaz.id,
            "day" to bugunKodu(),
            "time" to "-1"
        )
        if (cihaz.ciftHazne) {
            p["amount1"] = porsiyon1.toString()
            p["amount2"] = porsiyon2.toString()
        } else {
            p["amount"] = porsiyon1.toString()
        }
        val sonuc = api.postYenilemeli("${cihaz.tip}/saveDailyFeed", p)
        return when (sonuc) {
            is JsonPrimitive -> sonuc.content
            is JsonObject -> sonuc.ilkMetin("id", "feedId", "state")
            else -> null
        }
    }

    /** Sıradaki/işlenen beslemeyi iptal eder. */
    suspend fun beslemeIptal(cihaz: Cihaz, beslemeId: String) {
        api.postYenilemeli(
            "${cihaz.tip}/cancelRealtimeFeed",
            mapOf("deviceId" to cihaz.id, "day" to bugunKodu(), "id" to beslemeId)
        )
    }

    // ------------------------------------------------------------------ plan

    /** Cihaz ayrıntısındaki beslenme planını okur. */
    fun planlar(detay: JsonObject?): List<PlanOgun> {
        if (detay == null) return emptyList()
        val adaylar = listOf(
            detay.alan("multiFeedItem"),
            detay.alan("feed", "items"),
            detay.alan("feed"),
            detay.alan("feedDailyList"),
            detay.alan("items")
        )
        val ogunler = mutableListOf<PlanOgun>()
        for (aday in adaylar) {
            if (aday == null) continue
            val satirlar = planSatirlariBul(aday)
            if (satirlar.isNotEmpty()) {
                ogunler += satirlar
                break
            }
        }
        return ogunler.sortedBy { it.saniye ?: Int.MAX_VALUE }
    }

    private fun planSatirlariBul(el: JsonElement): List<PlanOgun> {
        val cikti = mutableListOf<PlanOgun>()

        fun satirMi(o: JsonObject): Boolean =
            o.tamsayi("time") != null && (o.tamsayi("amount") != null ||
                    o.tamsayi("amount1") != null || o.tamsayi("amount2") != null)

        fun gez(e: JsonElement, derinlik: Int) {
            if (derinlik > 4) return
            when (e) {
                is JsonArray -> e.forEach { gez(it, derinlik + 1) }
                is JsonObject -> {
                    if (satirMi(e)) {
                        cikti += PlanOgun(
                            id = e.ilkMetin("id", "feedId"),
                            gunKodu = e.ilkMetin("day", "days", "repeats"),
                            saniye = e.tamsayi("time"),
                            miktar1 = e.ilkTamsayi("amount1", "amount"),
                            miktar2 = e.tamsayi("amount2"),
                            ad = e.ilkMetin("name", "petName"),
                            aktif = (e.tamsayi("status") ?: e.tamsayi("state") ?: 0) != 1,
                            ham = e
                        )
                    } else {
                        e.values.forEach { gez(it, derinlik + 1) }
                    }
                }
                else -> {}
            }
        }
        gez(el, 0)
        return cikti
    }

    /**
     * Plana öğün ekler / mevcut öğünü güncelleştirir.
     * @param tekrarGunler 1=Pazartesi ... 7=Pazar. Boşsa yalnızca bugüne eklenir.
     */
    suspend fun planKaydet(
        cihaz: Cihaz,
        saat: Int,
        dakika: Int,
        porsiyon1: Int,
        porsiyon2: Int,
        tekrarGunler: Set<Int>,
        ad: String?,
        mevcutId: String? = null
    ) {
        val p = mutableMapOf(
            "deviceId" to cihaz.id,
            "time" to (saat * 3600 + dakika * 60).toString()
        )
        if (tekrarGunler.isEmpty()) {
            p["day"] = bugunKodu()
        } else {
            val sirali = tekrarGunler.sorted().joinToString(",")
            p["repeats"] = sirali
            p["days"] = sirali
            p["day"] = bugunKodu()
        }
        if (cihaz.ciftHazne) {
            p["amount1"] = porsiyon1.toString()
            p["amount2"] = porsiyon2.toString()
        } else {
            p["amount"] = porsiyon1.toString()
        }
        if (!ad.isNullOrBlank()) p["name"] = ad
        if (!mevcutId.isNullOrBlank()) p["id"] = mevcutId

        api.postYenilemeli("${cihaz.tip}/saveDailyFeed", p)
    }

    /** Plandan öğün siler. */
    suspend fun planSil(cihaz: Cihaz, ogun: PlanOgun) {
        val id = ogun.id ?: throw IllegalStateException("Bu öğünün kimliği okunamadı, silinemiyor.")
        api.postYenilemeli(
            "${cihaz.tip}/removeDailyFeed",
            mapOf("deviceId" to cihaz.id, "day" to (ogun.gunKodu ?: bugunKodu()), "id" to id)
        )
    }

    /** Silinmiş/duraklatılmış öğünü geri açar. */
    suspend fun planGeriAl(cihaz: Cihaz, ogun: PlanOgun) {
        val id = ogun.id ?: throw IllegalStateException("Bu öğünün kimliği okunamadı.")
        api.postYenilemeli(
            "${cihaz.tip}/restoreDailyFeed",
            mapOf("deviceId" to cihaz.id, "day" to (ogun.gunKodu ?: bugunKodu()), "id" to id)
        )
    }

    // ------------------------------------------------------------------ kayıtlar

    suspend fun kayitlar(cihaz: Cihaz, gunKodu: String): List<KayitSatiri> {
        val sonuc = api.postYenilemeli(
            "${cihaz.tip}/getDeviceRecord",
            mapOf("deviceId" to cihaz.id, "days" to gunKodu, "day" to gunKodu, "date" to gunKodu)
        )
        return kayitAyikla(sonuc)
    }

    private fun kayitAyikla(el: JsonElement): List<KayitSatiri> {
        val cikti = mutableListOf<KayitSatiri>()

        fun zamanBul(o: JsonObject): Long? {
            for (a in listOf("timestamp", "time", "completedAt", "createdAt", "eventTime", "date")) {
                val v = o.tamsayi(a) ?: continue
                if (v > 1_000_000_000) return v.toLong()
            }
            return null
        }

        fun satirEkle(o: JsonObject, ustTur: String?) {
            val icerik = (o["content"] as? JsonObject) ?: o
            val zaman = zamanBul(o) ?: zamanBul(icerik)
            val turKodu = o.ilkMetin("eventType", "event", "type", "subContent")
                ?: ustTur ?: "olay"
            val m1 = icerik.ilkTamsayi("amount1", "amount", "realAmount1")
            val m2 = icerik.ilkTamsayi("amount2", "realAmount2")
            val durum = icerik.ilkTamsayi("status", "state")
            cikti += KayitSatiri(
                zamanEpoch = zaman,
                zamanMetni = zaman?.let { saatBicimi.format(Date(it * 1000)) } ?: "-",
                tur = olayTuruTr(turKodu),
                aciklama = aciklamaOlustur(turKodu, m1, m2, durum, icerik),
                miktar1 = m1,
                miktar2 = m2,
                basarili = durum?.let { it == 0 || it == 1 },
                ham = o
            )
        }

        fun gez(e: JsonElement, ustTur: String?, derinlik: Int) {
            if (derinlik > 5) return
            when (e) {
                is JsonArray -> e.forEach { gez(it, ustTur, derinlik + 1) }
                is JsonObject -> {
                    val altItems = e["items"]
                    val tur = e.ilkMetin("eventType", "event", "type") ?: ustTur
                    if (altItems != null && (altItems is JsonArray)) {
                        gez(altItems, tur, derinlik + 1)
                    } else {
                        satirEkle(e, ustTur)
                    }
                }
                else -> {}
            }
        }
        gez(el, null, 0)
        return cikti.sortedByDescending { it.zamanEpoch ?: 0L }
    }

    private fun olayTuruTr(kod: String): String = when (kod.lowercase()) {
        "feed", "5" -> "Besleme"
        "eat", "10" -> "Yeme"
        "pet", "8" -> "Evcil hayvan algılandı"
        "food_warn", "food", "7" -> "Mama uyarısı"
        "battery", "6" -> "Pil"
        "desiccant" -> "Nem alıcı"
        "moved", "3" -> "Cihaz hareketi"
        "clean", "4" -> "Temizlik"
        "olay" -> "Olay"
        else -> "Olay ($kod)"
    }

    private fun aciklamaOlustur(
        tur: String,
        m1: Int?,
        m2: Int?,
        durum: Int?,
        icerik: JsonObject
    ): String {
        val g = prefs.porsiyonGram
        val parcalar = mutableListOf<String>()
        if (m1 != null && m1 > 0) parcalar += "Hazne 1: $m1 porsiyon (~${m1 * g} g)"
        if (m2 != null && m2 > 0) parcalar += "Hazne 2: $m2 porsiyon (~${m2 * g} g)"
        icerik.metin("mode")?.let { mod ->
            parcalar += when (mod) {
                "1" -> "Planlı"
                "0" -> "Elle"
                else -> "Mod: $mod"
            }
        }
        if (durum != null && durum != 0 && durum != 1) parcalar += "Durum kodu: $durum"
        icerik.metin("desc")?.let { parcalar += it }
        return if (parcalar.isEmpty()) tur else parcalar.joinToString(" · ")
    }

    // ------------------------------------------------------------------ ayarlar

    /** Cihaz ayarı günceller. Örnek: mapOf("lightMode" to 1) */
    suspend fun ayarGuncelle(cihaz: Cihaz, ayarlar: Map<String, Any>) {
        val kv = ayarlar.entries.joinToString(",", "{", "}") { (k, v) ->
            val deger = when (v) {
                is Number, is Boolean -> v.toString()
                else -> "\"$v\""
            }
            "\"$k\":$deger"
        }
        api.postYenilemeli(
            "${cihaz.tip}/updateSettings",
            mapOf("id" to cihaz.id, "deviceId" to cihaz.id, "kv" to kv)
        )
    }

    /** Ham JSON ile ayar gönderme (ileri düzey). */
    suspend fun ayarHamGuncelle(cihaz: Cihaz, kvJson: String) {
        api.postYenilemeli(
            "${cihaz.tip}/updateSettings",
            mapOf("id" to cihaz.id, "deviceId" to cihaz.id, "kv" to kvJson)
        )
    }

    /** Nem alıcıyı (kurutucu) sıfırla - yeni paket takıldığında. */
    suspend fun nemAliciSifirla(cihaz: Cihaz) {
        api.postYenilemeli("${cihaz.tip}/desiccantReset", mapOf("deviceId" to cihaz.id, "id" to cihaz.id))
    }

    /** Cihaz üzerinde komut çalıştır (kalibrasyon, mama sıfırlama vb.). */
    suspend fun komutGonder(cihaz: Cihaz, komutTipi: String, kvJson: String) {
        api.postYenilemeli(
            "${cihaz.tip}/controlDevice",
            mapOf("id" to cihaz.id, "deviceId" to cihaz.id, "type" to komutTipi, "kv" to kvJson)
        )
    }

    /** Cihazı yeniden adlandır. */
    suspend fun adDegistir(cihaz: Cihaz, yeniAd: String) {
        api.postYenilemeli(
            "${cihaz.tip}/update",
            mapOf("id" to cihaz.id, "deviceId" to cihaz.id, "kv" to "{\"name\":\"$yeniAd\"}", "name" to yeniAd)
        )
    }

    // ------------------------------------------------------------------ ham çağrı

    suspend fun hamCagri(yol: String, parametreler: Map<String, String>): String {
        val sonuc = api.postYenilemeli(yol, parametreler)
        return api.guzelJson(sonuc)
    }
}
