package com.teklif.yolcu.nav

import com.teklif.yolcu.geo.BorderCrossings
import com.teklif.yolcu.geo.Countries
import com.teklif.yolcu.geo.Country

/** Rota şekli üzerinde ülke değişimlerini bulur. */
object RouteBorders {

    private const val SAMPLE_M = 400.0

    fun compute(route: Route, countries: Countries): List<BorderEvent> {
        val shape = route.shape
        val cum = route.cumulative
        if (shape.size < 2) return emptyList()
        val events = ArrayList<BorderEvent>()

        // Rota boyunca ~400 m'de bir örnekle; değişim olunca iki örnek arasında ikiye bölerek noktayı bul.
        var prevIdx = 0
        var prevCountry: Country? = countries.at(shape[0].lat, shape[0].lon)
        var nextSampleAt = SAMPLE_M
        var i = 1
        while (i < shape.size) {
            if (cum[i] >= nextSampleAt || i == shape.size - 1) {
                nextSampleAt = cum[i] + SAMPLE_M
                val c = countries.at(shape[i].lat, shape[i].lon)
                if (c != null && prevCountry != null && c.iso2 != prevCountry.iso2) {
                    val crossIdx = refine(shape, prevIdx, i, prevCountry, countries)
                    val p = shape[crossIdx]
                    val crossing = BorderCrossings.nearest(p, prevCountry.iso2, c.iso2)
                    events.add(
                        BorderEvent(
                            shapeIndex = crossIdx,
                            distanceAlongM = cum[crossIdx],
                            fromIso = prevCountry.iso2,
                            fromName = prevCountry.nameTr,
                            toIso = c.iso2,
                            toName = c.nameTr,
                            crossingName = crossing?.name,
                            point = p,
                            schengenInternal = prevCountry.iso2 in Countries.SCHENGEN && c.iso2 in Countries.SCHENGEN,
                        )
                    )
                }
                if (c != null) {
                    prevCountry = c
                    prevIdx = i
                }
            }
            i++
        }
        // Aynı sınırın birkaç yüz metre içinde iki kez sayılmasını (sınır boyunca giden yol) engelle.
        val merged = ArrayList<BorderEvent>()
        for (e in events) {
            val last = merged.lastOrNull()
            if (last != null && e.distanceAlongM - last.distanceAlongM < 2000 && e.toIso == last.fromIso) {
                merged.removeAt(merged.size - 1) // geri döndü: ikisini de sil
            } else {
                merged.add(e)
            }
        }
        return merged
    }

    /** [lo] içeride, [hi] dışarıda: ilk dışarıdaki indeksi ikiye bölerek bulur. */
    private fun refine(shape: List<com.teklif.yolcu.geo.LatLon>, lo0: Int, hi0: Int, inside: Country, countries: Countries): Int {
        var lo = lo0
        var hi = hi0
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            val p = shape[mid]
            if (inside.contains(p.lat, p.lon)) lo = mid else hi = mid
        }
        return hi
    }
}
