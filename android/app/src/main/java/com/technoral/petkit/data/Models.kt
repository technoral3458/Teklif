package com.technoral.petkit.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// ---------------------------------------------------------------- JSON yardımcıları
// Petkit API'si cihaz yazılımına göre farklı alanlar döndürebildiği için
// katı veri sınıfları yerine toleranslı okuyucular kullanıyoruz.

fun JsonElement?.jsonArrayGuvenli(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> this.values.toList()
    else -> emptyList()
}

fun JsonElement?.nesne(): JsonObject? = this as? JsonObject

fun JsonObject?.alan(vararg yollar: String): JsonElement? {
    var g: JsonElement? = this
    for (y in yollar) {
        g = (g as? JsonObject)?.get(y) ?: return null
    }
    return if (g is JsonNull) null else g
}

fun JsonObject?.metin(vararg yollar: String): String? {
    val el = this.alan(*yollar) ?: return null
    return (el as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
}

fun JsonObject?.tamsayi(vararg yollar: String): Int? {
    val el = this.alan(*yollar) ?: return null
    val p = el as? JsonPrimitive ?: return null
    return p.content.toIntOrNull() ?: p.content.toDoubleOrNull()?.toInt()
}

fun JsonObject?.ondalik(vararg yollar: String): Double? {
    val el = this.alan(*yollar) ?: return null
    return (el as? JsonPrimitive)?.content?.toDoubleOrNull()
}

fun JsonObject?.mantik(vararg yollar: String): Boolean? {
    val el = this.alan(*yollar) ?: return null
    val c = (el as? JsonPrimitive)?.content ?: return null
    return when (c.lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> null
    }
}

/** İlk bulunan (boş olmayan) anahtarın tamsayı değeri. */
fun JsonObject?.ilkTamsayi(vararg anahtarlar: String): Int? {
    for (a in anahtarlar) this.tamsayi(a)?.let { return it }
    return null
}

fun JsonObject?.ilkMetin(vararg anahtarlar: String): String? {
    for (a in anahtarlar) this.metin(a)?.let { return it }
    return null
}

// ---------------------------------------------------------------- cihaz

/** Petkit hesabındaki bir cihaz. */
data class Cihaz(
    val id: String,
    val ad: String,
    val tip: String,          // API yolu için küçük harf: d4sh, d4s, d4h, d4, feeder...
    val tipEtiketi: String,   // kullanıcıya gösterilen model adı
    val seriNo: String?,
    val grupId: String?,
    val ham: JsonObject?
) {
    /** Bu model iki hazneli mi? (YumShare Dual-Hopper / Gemini) */
    val ciftHazne: Boolean get() = tip in setOf("d4s", "d4sh", "d4sd")

    /** Kamerası var mı? (YumShare serisi) */
    val kameraVar: Boolean get() = tip in setOf("d4sh", "d4h")

    val besleyiciMi: Boolean
        get() = tip.startsWith("d") || tip.startsWith("feeder")
}

object CihazTipleri {
    private val adlar = mapOf(
        "d4sh" to "YumShare Dual-Hopper (çift hazne + kamera)",
        "d4h" to "YumShare Solo (kamera)",
        "d4s" to "Fresh Element Gemini (çift hazne)",
        "d4" to "Fresh Element Solo",
        "d3" to "Fresh Element Infinity",
        "feeder" to "Fresh Element",
        "feedermini" to "Fresh Element Mini",
        "w5" to "Eversweet akıllı su pınarı",
        "ctw3" to "Eversweet 3 Pro",
        "t3" to "Pura X kum kabı",
        "t4" to "Pura MAX kum kabı",
        "t5" to "Purobot Ultra",
        "t6" to "Purobot MAX",
        "k2" to "Akıllı hava temizleyici",
        "k3" to "Akıllı hava temizleyici"
    )

    fun etiket(tip: String): String = adlar[tip.lowercase()] ?: "Petkit cihazı ($tip)"
}

// ---------------------------------------------------------------- besleyici durumu

/** Çift hazneli besleyici için özet durum. */
data class BesleyiciDurum(
    val ad: String,
    val cevrimici: Boolean,
    val pilYuzde: Int?,
    val pilDurumu: Int?,
    val prizeTakili: Boolean?,
    val wifiGuc: Int?,
    val nemAliciGun: Int?,
    val hazne1Dolu: Boolean?,
    val hazne2Dolu: Boolean?,
    val bugunPlanlanan1: Int?,
    val bugunVerilen1: Int?,
    val bugunPlanlanan2: Int?,
    val bugunVerilen2: Int?,
    val yemeSayisi: Int?,
    val hataKodu: Int?,
    val yazilim: String?,
    val seriNo: String?,
    val ham: JsonObject?
) {
    companion object {

        fun bos(ad: String, detay: JsonObject? = null) = BesleyiciDurum(
            ad = ad, cevrimici = false, pilYuzde = null, pilDurumu = null,
            prizeTakili = null, wifiGuc = null, nemAliciGun = null,
            hazne1Dolu = null, hazne2Dolu = null, bugunPlanlanan1 = null,
            bugunVerilen1 = null, bugunPlanlanan2 = null, bugunVerilen2 = null,
            yemeSayisi = null, hataKodu = null, yazilim = null, seriNo = null, ham = detay
        )

        fun ayikla(ad: String, detay: JsonObject?): BesleyiciDurum {
            if (detay == null) return bos(ad, null)
            val durum = detay.alan("state").nesne()
            val besleme = durum.alan("feedState").nesne()
            val pim = durum.ilkTamsayi("pim")
            val wifi = durum.alan("wifi").nesne().ilkTamsayi("rsq", "rssi")
            return BesleyiciDurum(
                ad = detay.metin("name") ?: ad,
                cevrimici = (pim ?: 0) > 0 || wifi != null,
                pilYuzde = durum.ilkTamsayi("batteryPower", "battery"),
                pilDurumu = durum.ilkTamsayi("batteryStatus"),
                prizeTakili = when (pim) {
                    1 -> true
                    2 -> false
                    else -> null
                },
                wifiGuc = wifi,
                nemAliciGun = durum.ilkTamsayi("desiccantLeftDays", "desiccantTime"),
                hazne1Dolu = durum.ilkTamsayi("food1", "food")?.let { it > 0 },
                hazne2Dolu = durum.ilkTamsayi("food2")?.let { it > 0 },
                bugunPlanlanan1 = besleme.ilkTamsayi("planAmountTotal1", "planAmountTotal"),
                bugunVerilen1 = besleme.ilkTamsayi("realAmountTotal1", "realAmountTotal"),
                bugunPlanlanan2 = besleme.ilkTamsayi("planAmountTotal2"),
                bugunVerilen2 = besleme.ilkTamsayi("realAmountTotal2"),
                yemeSayisi = besleme.ilkTamsayi("eatCount", "times"),
                hataKodu = durum.ilkTamsayi("errorCode"),
                yazilim = detay.ilkMetin("firmware", "hardware"),
                seriNo = detay.ilkMetin("sn", "secret"),
                ham = detay
            )
        }
    }
}

/** Beslenme planındaki tek bir öğün. */
data class PlanOgun(
    val id: String?,
    val gunKodu: String?,       // YYYYMMDD veya tekrar bilgisi
    val saniye: Int?,           // gün başından itibaren saniye
    val miktar1: Int?,
    val miktar2: Int?,
    val ad: String?,
    val aktif: Boolean,
    val ham: JsonObject?
) {
    val saatMetni: String
        get() {
            val s = saniye ?: return "--:--"
            if (s < 0) return "Anında"
            val sa = s / 3600
            val dk = (s % 3600) / 60
            return String.format("%02d:%02d", sa, dk)
        }
}

/** Cihaz kayıt/geçmiş satırı. */
data class KayitSatiri(
    val zamanEpoch: Long?,
    val zamanMetni: String,
    val tur: String,
    val aciklama: String,
    val miktar1: Int?,
    val miktar2: Int?,
    val basarili: Boolean?,
    val ham: JsonObject?
)
