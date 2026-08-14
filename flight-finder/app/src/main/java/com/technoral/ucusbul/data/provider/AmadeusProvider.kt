package com.technoral.ucusbul.data.provider

import com.technoral.ucusbul.core.Prefs
import com.technoral.ucusbul.data.FlightOffer
import com.technoral.ucusbul.data.FlightSegment
import com.technoral.ucusbul.data.LegQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * Amadeus Self-Service "Flight Offers Search".
 * GDS kaynaklı olduğu için tek bir havayoluna değil, yüzlerce taşıyıcıya bakar.
 * Ücretsiz anahtar: https://developers.amadeus.com  (Ayarlar ekranından girilir)
 */
class AmadeusProvider(private val prefs: Prefs) : FlightProvider {

    override val id = "amadeus"
    override val displayName = "Amadeus"

    private var token: String? = null
    private var tokenExpiresAt: Long = 0L

    private val host: String
        get() = if (prefs.amadeusProduction) "https://api.amadeus.com" else "https://test.api.amadeus.com"

    override fun isConfigured(): Boolean =
        prefs.amadeusKey.isNotBlank() && prefs.amadeusSecret.isNotBlank()

    private suspend fun token(): String = withContext(Dispatchers.IO) {
        val cached = token
        if (cached != null && System.currentTimeMillis() < tokenExpiresAt) return@withContext cached

        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", prefs.amadeusKey)
            .add("client_secret", prefs.amadeusSecret)
            .build()
        val req = Request.Builder()
            .url("$host/v1/security/oauth2/token")
            .post(body)
            .build()

        Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ProviderException("Amadeus girişi başarısız (${resp.code}). Anahtarları kontrol edin.")
            }
            val json = JSONObject(text)
            val access = json.optString("access_token")
            if (access.isBlank()) throw ProviderException("Amadeus token alınamadı.")
            val expiresIn = json.optInt("expires_in", 1500)
            token = access
            tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60).coerceAtLeast(60) * 1000L
            access
        }
    }

    override suspend fun search(query: LegQuery): List<FlightOffer> = withContext(Dispatchers.IO) {
        val bearer = token()
        val sb = StringBuilder("$host/v2/shopping/flight-offers")
        sb.append("?originLocationCode=").append(query.origin)
        sb.append("&destinationLocationCode=").append(query.destination)
        sb.append("&departureDate=").append(query.departureDate)
        query.returnDate?.let { sb.append("&returnDate=").append(it) }
        sb.append("&adults=").append(query.adults)
        sb.append("&currencyCode=").append(query.currency)
        sb.append("&max=").append(query.maxResults)
        if (query.nonStopOnly) sb.append("&nonStop=true")

        val req = Request.Builder()
            .url(sb.toString())
            .header("Authorization", "Bearer $bearer")
            .get()
            .build()

        Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            when {
                resp.code == 401 -> {
                    token = null
                    throw ProviderException("Amadeus yetkisi reddedildi (401).")
                }
                resp.code == 429 -> throw ProviderException("Amadeus istek limiti aşıldı (429).")
                !resp.isSuccessful -> {
                    // 400 çoğunlukla "bu havalimanı çifti için uçuş yok" demektir; sessizce boş dön.
                    if (resp.code == 400) return@use emptyList()
                    throw ProviderException("Amadeus hatası (${resp.code}).")
                }
            }
            parse(text, query)
        }
    }

    private fun parse(text: String, query: LegQuery): List<FlightOffer> {
        val root = JSONObject(text)
        val data = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<FlightOffer>(data.length())

        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val price = o.optJSONObject("price") ?: continue
            val total = price.optString("grandTotal").toDoubleOrNull()
                ?: price.optString("total").toDoubleOrNull() ?: continue
            val currency = price.optString("currency", query.currency)

            val itineraries = o.optJSONArray("itineraries") ?: continue
            val first = itineraries.optJSONObject(0) ?: continue
            val segmentsArr = first.optJSONArray("segments") ?: continue

            val segments = ArrayList<FlightSegment>(segmentsArr.length())
            val carriers = LinkedHashSet<String>()
            for (s in 0 until segmentsArr.length()) {
                val seg = segmentsArr.optJSONObject(s) ?: continue
                val dep = seg.optJSONObject("departure")
                val arr = seg.optJSONObject("arrival")
                val carrier = seg.optString("carrierCode")
                carriers.add(carrier)
                segments.add(
                    FlightSegment(
                        carrierCode = carrier,
                        flightNumber = seg.optString("number"),
                        from = dep?.optString("iataCode").orEmpty(),
                        to = arr?.optString("iataCode").orEmpty(),
                        departure = dep?.optString("at").orEmpty(),
                        arrival = arr?.optString("at").orEmpty()
                    )
                )
            }
            if (segments.isEmpty()) continue

            val validating = o.optJSONArray("validatingAirlineCodes")
            if (validating != null && validating.length() > 0) {
                carriers.add(validating.optString(0))
            }

            out.add(
                FlightOffer(
                    id = "amadeus-${o.optString("id", i.toString())}-${query.origin}${query.destination}",
                    provider = displayName,
                    origin = query.origin,
                    destination = query.destination,
                    price = total,
                    currency = currency,
                    airlineCodes = carriers.filter { it.isNotBlank() },
                    stops = segments.size - 1,
                    durationMinutes = parseIsoDuration(first.optString("duration")),
                    departure = segments.first().departure,
                    arrival = segments.last().arrival,
                    segments = segments
                )
            )
        }
        return out
    }

    companion object {
        /** "PT13H45M" -> 825 */
        fun parseIsoDuration(value: String?): Int {
            if (value.isNullOrBlank()) return 0
            val m = Regex("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?").find(value) ?: return 0
            val days = m.groupValues[1].toIntOrNull() ?: 0
            val hours = m.groupValues[2].toIntOrNull() ?: 0
            val mins = m.groupValues[3].toIntOrNull() ?: 0
            return days * 1440 + hours * 60 + mins
        }
    }
}
