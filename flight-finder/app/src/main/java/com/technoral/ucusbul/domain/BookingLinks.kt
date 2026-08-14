package com.technoral.ucusbul.domain

import com.technoral.ucusbul.data.FlightOffer
import java.net.URLEncoder

data class BookingOption(val title: String, val subtitle: String, val url: String)

/**
 * Uygulama bilet satmaz; bulduğu uçuşu satın alınabileceği yerlere yönlendirir.
 * Sağlayıcı doğrudan link verdiyse o en üstte, ardından havayolunun kendi sitesi ve
 * karşılaştırma siteleri gelir.
 */
object BookingLinks {

    fun optionsFor(
        offer: FlightOffer,
        departureDate: String,
        returnDate: String?,
        adults: Int
    ): List<BookingOption> {
        val out = ArrayList<BookingOption>(5)
        val from = offer.origin
        val to = offer.destination

        offer.deepLink?.let {
            out.add(BookingOption("Bu teklifi satın al", "${offer.provider} üzerinden doğrudan", it))
        }

        val airline = offer.mainAirline
        Airlines.site(airline)?.let {
            out.add(BookingOption("${Airlines.name(airline)} resmi sitesi", "Havayolundan doğrudan al", it))
        }

        out.add(
            BookingOption(
                "Google Flights",
                "Tüm havayollarını karşılaştır",
                googleFlights(from, to, departureDate, returnDate)
            )
        )
        out.add(
            BookingOption(
                "Skyscanner",
                "Acente fiyatlarını karşılaştır",
                skyscanner(from, to, departureDate, returnDate, adults)
            )
        )
        out.add(
            BookingOption(
                "Kayak",
                "Alternatif acenteler",
                kayak(from, to, departureDate, returnDate)
            )
        )
        return out
    }

    fun googleFlights(from: String, to: String, date: String, returnDate: String?): String {
        val q = buildString {
            append("Flights from ").append(from).append(" to ").append(to)
            append(" on ").append(date)
            returnDate?.let { append(" returning ").append(it) }
        }
        return "https://www.google.com/travel/flights?hl=tr&q=" + enc(q)
    }

    fun skyscanner(from: String, to: String, date: String, returnDate: String?, adults: Int): String {
        val d = compactDate(date)
        val r = returnDate?.let { compactDate(it) }
        val path = if (r != null) "$d/$r" else d
        return "https://www.skyscanner.com.tr/transport/flights/" +
            from.lowercase() + "/" + to.lowercase() + "/" + path + "/?adults=$adults"
    }

    fun kayak(from: String, to: String, date: String, returnDate: String?): String {
        val seg = if (returnDate != null) "$date/$returnDate" else date
        return "https://www.kayak.com.tr/flights/$from-$to/$seg?sort=price_a"
    }

    /** "2026-09-01" -> "260901" (Skyscanner biçimi) */
    private fun compactDate(iso: String): String =
        if (iso.length == 10) iso.substring(2, 4) + iso.substring(5, 7) + iso.substring(8, 10) else iso

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
