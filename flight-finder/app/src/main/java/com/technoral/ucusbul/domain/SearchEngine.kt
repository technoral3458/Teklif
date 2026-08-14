package com.technoral.ucusbul.domain

import android.content.Context
import com.technoral.ucusbul.core.Prefs
import com.technoral.ucusbul.data.Airport
import com.technoral.ucusbul.data.AirportRepository
import com.technoral.ucusbul.data.Geo
import com.technoral.ucusbul.data.LegQuery
import com.technoral.ucusbul.data.NearbyAirport
import com.technoral.ucusbul.data.OfferResult
import com.technoral.ucusbul.data.SearchOutcome
import com.technoral.ucusbul.data.SearchQuery
import com.technoral.ucusbul.data.SortMode
import com.technoral.ucusbul.data.provider.ProviderException
import com.technoral.ucusbul.data.provider.Providers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections

/**
 * Uygulamanın kalbi: hedef şehrin kendi havalimanının yanı sıra ona en fazla
 * X saat kara mesafesindeki havalimanlarını da bulur, hepsini tüm etkin sağlayıcılarda
 * paralel olarak tarar ve sonuçları tek listede birleştirir.
 */
class SearchEngine(context: Context) {

    private val appContext = context.applicationContext
    private val repo = AirportRepository.get(appContext)
    private val prefs = Prefs.get(appContext)

    suspend fun run(query: SearchQuery, onProgress: (String) -> Unit = {}): SearchOutcome {
        val destAnchor = repo.byIata(query.destinationIata)
            ?: return SearchOutcome(notes = listOf("Varış havalimanı bulunamadı: ${query.destinationIata}"))
        val originAnchor = repo.byIata(query.originIata)
            ?: return SearchOutcome(notes = listOf("Kalkış havalimanı bulunamadı: ${query.originIata}"))

        val destinations = repo.nearby(
            anchor = destAnchor,
            maxGroundMinutes = query.maxGroundMinutes,
            transport = query.transport,
            includeSmall = query.includeSmallAirports,
            limit = query.maxDestinations
        )

        val origins: List<Airport> = if (query.searchNearbyOrigins) {
            repo.nearby(
                anchor = originAnchor,
                maxGroundMinutes = query.maxGroundMinutes,
                transport = query.transport,
                includeSmall = query.includeSmallAirports,
                limit = prefs.maxOrigins
            ).map { it.airport }
        } else {
            repo.cityAirports(originAnchor).take(prefs.maxOrigins)
        }

        val providers = Providers.active(prefs)
        if (providers.isEmpty()) {
            return SearchOutcome(
                scanned = destinations,
                notes = listOf(
                    "Hiçbir uçuş sağlayıcısı ayarlı değil. Ayarlar ekranından Amadeus veya " +
                        "Travelpayouts anahtarı girin ya da demo modunu açın."
                )
            )
        }

        onProgress("${destinations.size} varış × ${origins.size} kalkış havalimanı taranıyor…")

        val notes = Collections.synchronizedList(ArrayList<String>())
        val gate = Semaphore(MAX_PARALLEL)

        val results: List<OfferResult> = coroutineScope {
            val jobs = ArrayList<kotlinx.coroutines.Deferred<List<OfferResult>>>()
            for (dest in destinations) {
                for (origin in origins) {
                    if (origin.iata == dest.airport.iata) continue
                    for (provider in providers) {
                        jobs.add(
                            async {
                                gate.withPermit {
                                    val leg = LegQuery(
                                        origin = origin.iata,
                                        destination = dest.airport.iata,
                                        departureDate = query.departureDate,
                                        returnDate = query.returnDate,
                                        adults = query.adults,
                                        currency = prefs.currency,
                                        nonStopOnly = query.nonStopOnly,
                                        distanceKm = Geo.distanceKm(
                                            origin.lat, origin.lon,
                                            dest.airport.lat, dest.airport.lon
                                        )
                                    )
                                    try {
                                        provider.search(leg).map { OfferResult(it, dest, origin) }
                                    } catch (e: ProviderException) {
                                        notes.add(e.message ?: "Sağlayıcı hatası")
                                        emptyList()
                                    } catch (e: Exception) {
                                        notes.add(
                                            "${provider.displayName} • ${origin.iata}→${dest.airport.iata}: " +
                                                (e.message ?: e.javaClass.simpleName)
                                        )
                                        emptyList()
                                    }
                                }
                            }
                        )
                    }
                }
            }
            jobs.awaitAll().flatten()
        }

        val deduped = results
            .distinctBy { listOf(it.offer.provider, it.offer.origin, it.offer.destination, it.offer.price, it.offer.departure, it.offer.stops) }

        return SearchOutcome(
            results = sort(deduped, SortMode.PRICE),
            scanned = destinations,
            notes = notes.distinct().take(6),
            usedDemo = deduped.any { it.offer.isDemo }
        )
    }

    companion object {
        private const val MAX_PARALLEL = 4

        fun sort(list: List<OfferResult>, mode: SortMode): List<OfferResult> = when (mode) {
            SortMode.PRICE -> list.sortedWith(compareBy({ it.offer.price }, { it.totalMinutes }))
            SortMode.TOTAL_TIME -> list.sortedWith(compareBy({ it.totalMinutes }, { it.offer.price }))
            SortMode.SMART -> {
                if (list.size < 2) list else {
                    val minP = list.minOf { it.offer.price }
                    val maxP = list.maxOf { it.offer.price }
                    val minT = list.minOf { it.totalMinutes }
                    val maxT = list.maxOf { it.totalMinutes }
                    val pSpan = (maxP - minP).takeIf { it > 0.0 } ?: 1.0
                    val tSpan = (maxT - minT).takeIf { it > 0 } ?: 1
                    list.sortedBy {
                        val pn = (it.offer.price - minP) / pSpan
                        val tn = (it.totalMinutes - minT).toDouble() / tSpan
                        0.65 * pn + 0.35 * tn
                    }
                }
            }
        }
    }
}

/** Yakın havalimanı rozeti için kısa açıklama. */
fun NearbyAirport.badge(): String = if (isAnchor) {
    "Ana havalimanı"
} else {
    "$distanceKm km • ${com.technoral.ucusbul.data.formatMinutes(groundMinutes)} kara yolu"
}
