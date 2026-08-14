package com.technoral.ucusbul.data.provider

import com.technoral.ucusbul.data.FlightOffer
import com.technoral.ucusbul.data.FlightSegment
import com.technoral.ucusbul.data.LegQuery
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Hiçbir API anahtarı girilmemişken uygulamanın tüm akışının (yakın havalimanı taraması,
 * sıralama, fiyat takibi, satın alma yönlendirmesi) denenebilmesi için gerçekçi ama
 * **gerçek olmayan** fiyatlar üretir. Sonuçlar ekranda DEMO etiketiyle gösterilir.
 * Aynı sorgu her zaman aynı sonucu verir, böylece fiyat takibi de tutarlı çalışır.
 */
class DemoProvider : FlightProvider {

    override val id = "demo"
    override val displayName = "Demo"

    override fun isConfigured(): Boolean = true

    override suspend fun search(query: LegQuery): List<FlightOffer> {
        val seed = (query.origin + query.destination + query.departureDate).hashCode().toLong()
        val rnd = Random(seed)

        val km = if (query.distanceKm > 1.0) query.distanceKm else 2500.0
        // Kabaca gerçekçi bir taban: mesafeye bağlı km başına ücret + sabit maliyet.
        val base = 900.0 + km * 1.45

        val count = 3 + rnd.nextInt(4)
        val out = ArrayList<FlightOffer>(count)
        for (i in 0 until count) {
            val stops = if (i == 0 && km < 3000) 0 else rnd.nextInt(3).coerceAtMost(2)
            val stopDiscount = 1.0 - stops * 0.11
            val noise = 0.80 + rnd.nextDouble() * 0.55
            val price = (base * stopDiscount * noise / 10.0).roundToInt() * 10.0

            val carriers = DEMO_CARRIERS.shuffled(rnd).take(if (stops == 0) 1 else 2)
            val flightMinutes = (km / 780.0 * 60.0).roundToInt() + 45 + stops * (70 + rnd.nextInt(180))
            val depHour = 6 + rnd.nextInt(16)
            val depMin = listOf(0, 5, 15, 25, 30, 40, 45, 55)[rnd.nextInt(8)]
            val dep = "%sT%02d:%02d:00".format(query.departureDate, depHour, depMin)

            out.add(
                FlightOffer(
                    id = "demo-$i-${query.origin}${query.destination}-${query.departureDate}",
                    provider = displayName,
                    origin = query.origin,
                    destination = query.destination,
                    price = price * query.adults,
                    currency = query.currency,
                    airlineCodes = carriers,
                    stops = stops,
                    durationMinutes = flightMinutes,
                    departure = dep,
                    arrival = "",
                    segments = listOf(
                        FlightSegment(
                            carrierCode = carriers.first(),
                            flightNumber = (100 + rnd.nextInt(899)).toString(),
                            from = query.origin,
                            to = query.destination,
                            departure = dep,
                            arrival = ""
                        )
                    ),
                    isDemo = true
                )
            )
        }
        return out.sortedBy { it.price }
    }

    private companion object {
        val DEMO_CARRIERS = listOf(
            "TK", "PC", "QR", "EK", "SU", "CA", "MU", "CZ", "HU", "LH",
            "AF", "KL", "SV", "GF", "WY", "AZ", "UX", "KC", "J2", "3U"
        )
    }
}
