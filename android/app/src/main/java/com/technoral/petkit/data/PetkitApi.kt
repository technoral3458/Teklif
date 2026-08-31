package com.technoral.petkit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Petkit bölge sunucuları. */
data class PetkitBolge(val kod: String, val ad: String, val taban: String) {
    companion object {
        val GLOBAL = PetkitBolge("TR", "Türkiye / Avrupa / ABD (global sunucu)", "https://api.petkt.com/latest/")
        val ASYA = PetkitBolge("SG", "Asya - Pasifik", "https://api.petktasia.com/latest/")
        val CIN = PetkitBolge("CN", "Çin ana kara", "http://api.petkit.cn/6/")
        val VARSAYILAN = GLOBAL
        val HEPSI = listOf(GLOBAL, ASYA, CIN)
        const val PASAPORT = "https://passport.petkt.com/6/"
    }
}

/** API sonucu: ya veri ya hata. */
sealed class ApiSonuc<out T> {
    data class Basarili<T>(val veri: T) : ApiSonuc<T>()
    data class Hata(val mesaj: String, val kod: Int? = null, val oturumDustu: Boolean = false) :
        ApiSonuc<Nothing>()
}

class PetkitOturumHatasi(mesaj: String) : Exception(mesaj)

/**
 * Petkit bulut API istemcisi.
 *
 * Bu API resmi olarak belgelenmemiştir; uç noktalar ve alan adları topluluk
 * çalışmalarından derlenmiştir. Bu yüzden istemci "toleranslı" yazılmıştır:
 * bilinmeyen alanlar yoksayılır, her istek/yanıt [ApiLog] içine kaydedilir ve
 * uygulamadaki API Konsolu ekranından ham istek gönderilebilir.
 */
class PetkitApi(private val prefs: Prefs) {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val jsonYazdir = Json { prettyPrint = true; isLenient = true }

    private val istemci: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ----------------------------------------------------------------- yardımcılar

    private val saatDilimiId: String get() = TimeZone.getDefault().id

    private val saatDilimiFark: String
        get() {
            val ofsetDk = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
            return String.format(Locale.US, "%.1f", ofsetDk / 60.0)
        }

    private fun istemciBilgisi(): String = buildString {
        append("{")
        append("\"locale\":\"tr_TR\",")
        append("\"name\":\"Android\",")
        append("\"osVersion\":\"${android.os.Build.VERSION.RELEASE}\",")
        append("\"platform\":\"android\",")
        append("\"source\":\"app.petkit-android\",")
        append("\"timezone\":\"$saatDilimiFark\",")
        append("\"timezoneId\":\"$saatDilimiId\",")
        append("\"token\":\"\",")
        append("\"version\":\"$ISTEMCI_SURUM\"")
        append("}")
    }

    private fun Request.Builder.ortakBasliklar(): Request.Builder = apply {
        header("Accept", "*/*")
        header("Accept-Language", "tr-TR;q=1, en-US;q=0.9")
        header("User-Agent", "PETKIT/$ISTEMCI_SURUM (Android ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MODEL})")
        header("X-Timezone", saatDilimiFark)
        header("X-TimezoneId", saatDilimiId)
        header("X-Api-Version", ISTEMCI_SURUM)
        header("X-Img-Version", "1")
        header("X-Locale", "tr_TR")
        header("X-Client", "Android(${android.os.Build.VERSION.RELEASE};${android.os.Build.MODEL})")
        prefs.session?.let {
            header("X-Session", it)
            header("F-Session", it)
        }
    }

    private fun tabanUrl(): String {
        val t = prefs.sunucuUrl.trim()
        return if (t.endsWith("/")) t else "$t/"
    }

    /**
     * Ham POST (form-urlencoded) çağrısı. Dönüş: API'nin "result" alanı.
     * Hata durumunda [PetkitOturumHatasi] veya [IllegalStateException] fırlatır.
     */
    suspend fun post(
        yol: String,
        parametreler: Map<String, String>,
        tamTabanUrl: String? = null
    ): JsonElement = withContext(Dispatchers.IO) {
        val taban = tamTabanUrl ?: tabanUrl()
        val url = (taban + yol.removePrefix("/")).toHttpUrlOrNull()
            ?: throw IllegalStateException("Geçersiz sunucu adresi: $taban$yol")

        val govde = FormBody.Builder().apply {
            parametreler.forEach { (k, v) -> add(k, v) }
        }.build()

        val istek = Request.Builder().url(url).post(govde).ortakBasliklar().build()
        val istekMetni = gunlukIcinMetin(parametreler)
        val baslangic = System.currentTimeMillis()

        var gunlugeYazildi = false
        try {
            istemci.newCall(istek).execute().use { yanit ->
                val govdeMetni = yanit.body?.string().orEmpty()
                val sure = System.currentTimeMillis() - baslangic
                // Giriş yanıtı oturum kimliğini taşıdığı için günlüğe yazılmaz.
                val gunlukGovde =
                    if (yol.contains("login", ignoreCase = true))
                        "(giriş yanıtı gizlendi - oturum kimliği içerir)"
                    else govdeMetni.take(8000)
                try {
                    val ayristirilmis = cozumle(govdeMetni, yanit.code)
                    ApiLog.ekle(yol, istekMetni, gunlukGovde, true, sure)
                    gunlugeYazildi = true
                    return@withContext ayristirilmis
                } catch (e: Exception) {
                    // Sunucunun ham yanıtı günlüğe yazılır: hangi alanın
                    // reddedildiğini görmenin tek yolu bu.
                    ApiLog.ekle(
                        yol, istekMetni,
                        "HTTP ${yanit.code} · $gunlukGovde",
                        false, sure
                    )
                    gunlugeYazildi = true
                    throw e
                }
            }
        } catch (e: Exception) {
            if (!gunlugeYazildi) {
                ApiLog.ekle(
                    yol, istekMetni,
                    "BAĞLANTI HATASI: ${e.javaClass.simpleName}: ${e.message}",
                    false, System.currentTimeMillis() - baslangic
                )
            }
            throw e
        }
    }

    /** Ham GET çağrısı (bölge sunucu listesi gibi uç noktalar için). */
    suspend fun get(tamUrl: String): JsonElement = withContext(Dispatchers.IO) {
        val url = tamUrl.toHttpUrlOrNull() ?: throw IllegalStateException("Geçersiz adres: $tamUrl")
        val istek = Request.Builder().url(url).get().ortakBasliklar().build()
        val baslangic = System.currentTimeMillis()
        try {
            istemci.newCall(istek).execute().use { yanit ->
                val govde = yanit.body?.string().orEmpty()
                val sonuc = cozumle(govde, yanit.code)
                ApiLog.ekle(tamUrl, "GET", govde.take(8000), true, System.currentTimeMillis() - baslangic)
                return@withContext sonuc
            }
        } catch (e: Exception) {
            ApiLog.ekle(tamUrl, "GET", "HATA: ${e.message}", false, System.currentTimeMillis() - baslangic)
            throw e
        }
    }

    /**
     * Günlüğe yazılacak istek metni. Şifre özeti ve kullanıcı adı gibi
     * hassas alanlar maskelenir; günlük panoya kopyalanıp paylaşılabildiği
     * için ham hâlleri yazılmaz.
     */
    private fun gunlukIcinMetin(parametreler: Map<String, String>): String =
        parametreler.entries.joinToString("&") { (k, v) ->
            val deger = when (k.lowercase()) {
                "password", "oldpassword", "token", "session" -> "***"
                "username", "account", "email", "mobile" -> maskele(v)
                else -> v
            }
            "$k=$deger"
        }

    private fun maskele(metin: String): String = when {
        metin.length <= 3 -> "***"
        metin.contains("@") -> metin.take(2) + "***@" + metin.substringAfter("@")
        else -> metin.take(2) + "***" + metin.takeLast(2)
    }

    /** Petkit yanıt zarfını açar: {"result":...} / {"error":{"code":..,"msg":".."}} */
    private fun cozumle(govde: String, httpKod: Int): JsonElement {
        if (govde.isBlank()) {
            if (httpKod !in 200..299) throw IllegalStateException("Sunucu yanıtı boş (HTTP $httpKod)")
            return JsonNull
        }
        val kok = try {
            json.parseToJsonElement(govde)
        } catch (e: Exception) {
            throw IllegalStateException("Yanıt okunamadı (HTTP $httpKod): ${govde.take(200)}")
        }
        val nesne = kok as? JsonObject ?: return kok

        nesne["error"]?.let { hataEl ->
            if (hataEl is JsonObject) {
                val kod = hataEl["code"]?.jsonPrimitive?.content?.toIntOrNull()
                val msg = hataEl["msg"]?.jsonPrimitive?.content
                    ?: hataEl["message"]?.jsonPrimitive?.content
                    ?: "Bilinmeyen hata"
                if (kod in OTURUM_HATA_KODLARI) throw PetkitOturumHatasi(msg)
                throw IllegalStateException(hataMesajiCevir(kod, msg))
            }
        }
        nesne["result"]?.let { return it }
        if (httpKod !in 200..299) throw IllegalStateException("HTTP $httpKod: ${govde.take(200)}")
        return nesne
    }

    private fun hataMesajiCevir(kod: Int?, msg: String): String = when (kod) {
        1 -> "İstek hatalı (parametreler kabul edilmedi): $msg"
        5 -> "Oturum geçersiz, tekrar giriş yapılmalı."
        122 -> "Kullanıcı adı veya şifre hatalı."
        125 -> "Bu hesap bulunamadı. Bölge/sunucu seçimini kontrol edin."
        else -> if (kod != null) "Petkit hatası $kod: $msg" else msg
    }

    // ----------------------------------------------------------------- oturum

    suspend fun bolgeSunucularini_getir(): List<PetkitBolge> {
        val sonuc = get(PetkitBolge.PASAPORT + "v1/regionservers")
        val liste = (sonuc as? JsonObject)?.get("list")
        val cikti = mutableListOf<PetkitBolge>()
        if (liste != null) {
            liste.jsonArrayGuvenli().forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val ad = o.metin("name") ?: o.metin("id") ?: return@forEach
                val gw = o.metin("gateway") ?: return@forEach
                val kod = o.metin("id") ?: ad
                cikti += PetkitBolge(kod, ad, if (gw.endsWith("/")) gw else "$gw/")
            }
        }
        return cikti
    }

    /** Giriş yapar ve oturumu [Prefs] içine yazar. */
    suspend fun girisYap(kullanici: String, sifreDuzMetin: String, bolgeKodu: String): String {
        val md5 = md5(sifreDuzMetin)
        return girisYapMd5(kullanici, md5, bolgeKodu)
    }

    suspend fun girisYapMd5(kullanici: String, sifreMd5: String, bolgeKodu: String): String {
        prefs.oturumuTemizle()
        val sonuc = post(
            "user/login",
            mapOf(
                "client" to istemciBilgisi(),
                "encrypt" to "1",
                "oldVersion" to ISTEMCI_SURUM,
                "region" to bolgeKodu,
                "username" to kullanici,
                "password" to sifreMd5
            )
        )
        val nesne = sonuc as? JsonObject ?: throw IllegalStateException("Giriş yanıtı beklenmedik biçimde.")
        val oturum = nesne["session"]?.jsonObject
        val id = oturum?.metin("id")
            ?: nesne.metin("session")
            ?: throw IllegalStateException("Giriş başarılı görünüyor ama oturum kimliği bulunamadı.")

        prefs.session = id
        prefs.kullanici = kullanici
        prefs.sifreMd5 = sifreMd5
        prefs.bolgeKodu = bolgeKodu
        prefs.kullaniciAdiGoster = nesne["user"]?.jsonObject?.metin("nick")
            ?: nesne["user"]?.jsonObject?.metin("username")
        return id
    }

    /** Oturum düştüğünde kayıtlı bilgilerle sessizce yeniden giriş dener. */
    suspend fun yenidenGirisDene(): Boolean {
        val k = prefs.kullanici ?: return false
        val s = prefs.sifreMd5 ?: return false
        return try {
            girisYapMd5(k, s, prefs.bolgeKodu)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Oturum düşerse bir kez otomatik yeniden giriş yapıp isteği tekrarlar.
     */
    suspend fun postYenilemeli(yol: String, parametreler: Map<String, String>): JsonElement {
        return try {
            post(yol, parametreler)
        } catch (e: PetkitOturumHatasi) {
            if (yenidenGirisDene()) post(yol, parametreler)
            else throw IllegalStateException("Oturum süresi doldu, lütfen yeniden giriş yapın.")
        }
    }

    fun guzelJson(el: JsonElement?): String =
        if (el == null) "-" else try { jsonYazdir.encodeToString(JsonElement.serializer(), el) } catch (e: Exception) { el.toString() }

    companion object {
        const val ISTEMCI_SURUM = "11.3.1"
        private val OTURUM_HATA_KODLARI = setOf(5, 3, 401)

        fun md5(metin: String): String {
            val ozet = MessageDigest.getInstance("MD5").digest(metin.toByteArray(Charsets.UTF_8))
            return ozet.joinToString("") { "%02x".format(it) }
        }
    }
}
