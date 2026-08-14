package com.technoral.ucusbul.data.provider

import com.technoral.ucusbul.core.Prefs
import com.technoral.ucusbul.data.FlightOffer
import com.technoral.ucusbul.data.LegQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Travelpayouts / Aviasales fiyat API'si. Yüzlerce acente ve havayolunun fiyatlarını
 * tarar ve doğrudan satın alma linki döner.
 * Ücretsiz token: https://www.travelpayouts.com  (Ayarlar ekranından girilir)
 */
class TravelpayoutsProvider(private val prefs: Prefs) : FlightProvider {

    override val id = "travelpayouts"
    override val displayName = "Travelpayouts"

    override fun isConfigured(): Boolean = prefs.travelpayoutsToken.isNotBlank()

    override suspend fun search(query: LegQuery): List<FlightOffer> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("https://api.travelpayouts.com/aviasales/v3/prices_for_dates")
        sb.append("?origin=").append(query.origin)
        sb.append("&destination=").append(query.destination)
        sb.append("&departure_at=").append(query.departureDate)
        query.returnDate?.let { sb.append("&return_at=").append(it) }
        sb.append("&currency=").append(query.currency.lowercase())
        sb.append("&sorting=price")
        sb.append("&direct=").append(if (query.nonStopOnly) "true" else "false")
        sb.append("&limit=").append(query.maxResults)
        sb.append("&one_way=").append(if (query.returnDate == null) "true" else "false")
        sb.append("&token=").append(prefs.travelpayoutsToken)

        val req = Request.Builder().url(sb.toString()).get().build()

        Http.client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401 || resp.code == 403) {
                throw ProviderException("Travelpayouts token reddedildi (${resp.code}).")
            }
            if (!resp.isSuccessful) return@use emptyList()
            parse(text, query)
        }
    }

    private fun parse(text: String, query: LegQuery): List<FlightOffer> {
        val root = JSONObject(text)
        val data = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<FlightOffer>(data.length())
        val marker = prefs.travelpayoutsMarker

        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val price = o.optDouble("price", -1.0)
            if (price <= 0) continue

            val link = o.optString("link").takeIf { it.isNotBlank() }?.let { raw ->
                val base = if (raw.startsWith("http")) raw else "https://www.aviasales.com$raw"
                if (marker.isNotBlank()) {
                    base + (if (base.contains("?")) "&" else "?") + "marker=" + marker
                } else base
            }

            val duration = o.optInt("duration", 0).takeIf { it > 0 }
                ?: o.optInt("duration_to", 0)

            val airline = o.optString("airline")
            out.add(
                FlightOffer(
                    id = "tp-$i-${query.origin}${query.destination}-${o.optString("flight_number")}",
                    provider = displayName,
                    origin = o.optString("origin", query.origin),
                    destination = o.optString("destination", query.destination),
                    price = price,
                    currency = query.currency,
                    airlineCodes = if (airline.isBlank()) emptyList() else listOf(airline),
                    stops = o.optInt("transfers", 0),
                    durationMinutes = duration,
                    departure = o.optString("departure_at"),
                    arrival = o.optString("return_at"),
                    deepLink = link
                )
            )
        }
        return out
    }
}
