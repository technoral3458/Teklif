package com.technoral.servis.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Bir kur sorgusunun sonucu. */
data class RateQuote(
    val usd: Double,
    val eur: Double,
    val dateLabel: String,
    val source: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/**
 * Güncel döviz kurunu internetten çeker.
 *
 * Birincil kaynak TCMB'nin günlük kur bülteni (döviz satış kuru). Bülten hafta
 * sonu ve resmî tatillerde yayımlanmadığı için son yayımlanan güne kadar geriye
 * gidilir. TCMB'ye ulaşılamazsa ECB verisi kullanan frankfurter.app'e düşülür.
 */
object RateService {

    private const val TIMEOUT_MS = 15_000
    private const val TCMB_TODAY = "https://www.tcmb.gov.tr/kurlar/today.xml"

    /** Kurun "bayatlamış" sayılacağı süre. */
    const val STALE_AFTER_MS = 12 * 60 * 60 * 1000L

    fun isStale(settings: AppSettings): Boolean {
        if (settings.usdRate <= 0 || settings.eurRate <= 0) return true
        val updated = settings.ratesUpdatedAt ?: return true
        return System.currentTimeMillis() - updated > STALE_AFTER_MS
    }

    suspend fun fetch(): Result<RateQuote> = withContext(Dispatchers.IO) {
        tcmb()?.let { return@withContext Result.success(it) }
        frankfurter()?.let { return@withContext Result.success(it) }
        Result.failure(
            IllegalStateException(
                "Kur bilgisi alınamadı. İnternet bağlantınızı kontrol edin ya da kuru elle girin."
            )
        )
    }

    // ------------------------------------------------------------------ TCMB

    private fun tcmb(): RateQuote? {
        val urls = mutableListOf(TCMB_TODAY)
        // Bülten yoksa son 10 güne kadar geriye git
        val calendar = Calendar.getInstance()
        val folderFormat = SimpleDateFormat("yyyyMM", Locale.US)
        val fileFormat = SimpleDateFormat("ddMMyyyy", Locale.US)
        repeat(10) {
            val day = calendar.time
            urls.add(
                "https://www.tcmb.gov.tr/kurlar/${folderFormat.format(day)}/${fileFormat.format(day)}.xml"
            )
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        urls.forEach { url ->
            val xml = download(url, "ISO-8859-9") ?: return@forEach
            val usd = parseTcmbRate(xml, "USD")
            val eur = parseTcmbRate(xml, "EUR")
            if (usd != null && eur != null) {
                return RateQuote(
                    usd = usd,
                    eur = eur,
                    dateLabel = Regex("""Tarih="([^"]+)"""").find(xml)?.groupValues?.get(1).orEmpty(),
                    source = "TCMB döviz satış",
                )
            }
        }
        return null
    }

    /** Bir para biriminin TL satış kurunu döndürür; `Unit` alanına göre normalize eder. */
    internal fun parseTcmbRate(xml: String, code: String): Double? {
        val block = Regex(
            """<Currency[^>]*Kod="$code"[^>]*>(.*?)</Currency>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(xml)?.groupValues?.get(1) ?: return null

        val unit = Regex("""<Unit>\s*([\d.,]+)\s*</Unit>""")
            .find(block)?.groupValues?.get(1)?.toRate() ?: 1.0

        val value = listOf("ForexSelling", "BanknoteSelling", "ForexBuying", "BanknoteBuying")
            .firstNotNullOfOrNull { tag ->
                Regex("""<$tag>\s*([\d.,]+)\s*</$tag>""")
                    .find(block)?.groupValues?.get(1)?.toRate()?.takeIf { it > 0 }
            } ?: return null

        return if (unit > 0) value / unit else value
    }

    // ----------------------------------------------------------- yedek kaynak

    private fun frankfurter(): RateQuote? {
        val json = download("https://api.frankfurter.app/latest?from=TRY&to=USD,EUR", "UTF-8")
            ?: return null
        // TRY bazlı geldiği için ters çevriliyor: 1 USD = 1 / (USD/TRY)
        val usdPerTry = jsonNumber(json, "USD") ?: return null
        val eurPerTry = jsonNumber(json, "EUR") ?: return null
        if (usdPerTry <= 0 || eurPerTry <= 0) return null
        return RateQuote(
            usd = 1.0 / usdPerTry,
            eur = 1.0 / eurPerTry,
            dateLabel = Regex(""""date"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1).orEmpty(),
            source = "ECB (frankfurter.app)",
        )
    }

    internal fun jsonNumber(json: String, key: String): Double? =
        Regex(""""$key"\s*:\s*([\d.eE+-]+)""").find(json)?.groupValues?.get(1)?.toDoubleOrNull()

    // ------------------------------------------------------------ yardımcılar

    private fun String.toRate(): Double? = trim().replace(",", ".").toDoubleOrNull()

    private fun download(url: String, charsetName: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "DeliKadirApp/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream
                .bufferedReader(java.nio.charset.Charset.forName(charsetName))
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
