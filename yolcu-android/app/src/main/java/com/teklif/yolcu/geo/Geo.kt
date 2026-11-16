package com.teklif.yolcu.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Enlem/boylam (derece). */
data class LatLon(val lat: Double, val lon: Double)

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** İki nokta arası büyük daire uzaklığı (metre). */
    fun distance(a: LatLon, b: LatLon): Double = distance(a.lat, a.lon, b.lat, b.lon)

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** a'dan b'ye yön (0..360, kuzey 0). */
    fun bearing(a: LatLon, b: LatLon): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /** a-b doğru parçasına en yakın nokta ve oran (0..1), düz yaklaşımla (kısa parçalar için yeterli). */
    fun projectOnSegment(p: LatLon, a: LatLon, b: LatLon): Pair<LatLon, Double> {
        val kx = cos(Math.toRadians(p.lat))
        val ax = (a.lon - p.lon) * kx
        val ay = a.lat - p.lat
        val bx = (b.lon - p.lon) * kx
        val by = b.lat - p.lat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t) to t
    }

    // ---- Web Mercator (harita karoları) ----

    /** Boylamı, verilen yakınlaştırmada karo x koordinatına (kesirli) çevirir. */
    fun lonToTileX(lon: Double, zoom: Double): Double = (lon + 180.0) / 360.0 * 2.0.pow(zoom)

    fun latToTileY(lat: Double, zoom: Double): Double {
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return (1.0 - ln(tan(r) + 1 / cos(r)) / PI) / 2.0 * 2.0.pow(zoom)
    }

    fun tileXToLon(x: Double, zoom: Double): Double = x / 2.0.pow(zoom) * 360.0 - 180.0

    fun tileYToLat(y: Double, zoom: Double): Double {
        val n = PI - 2.0 * PI * y / 2.0.pow(zoom)
        return Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    }

    /** Bu enlemde, bu yakınlaştırmada bir ekran pikselinin metre karşılığı (256 px karo). */
    fun metersPerPixel(lat: Double, zoom: Double): Double =
        156_543.03392 * cos(Math.toRadians(lat)) / 2.0.pow(zoom)

    // ---- Çokgen ----

    /** Nokta halka içinde mi (ışın yöntemi). Halka: [lon, lat] çiftleri. */
    fun pointInRing(lat: Double, lon: Double, ring: DoubleArray): Boolean {
        var inside = false
        var j = ring.size - 2
        var i = 0
        while (i < ring.size) {
            val xi = ring[i]
            val yi = ring[i + 1]
            val xj = ring[j]
            val yj = ring[j + 1]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
            i += 2
        }
        return inside
    }

    // ---- Google/Valhalla kodlanmış çizgi ----

    /** Kodlanmış çizgiyi çözer. Valhalla 1e6, OSRM/Google 1e5 hassasiyet kullanır. */
    fun decodePolyline(encoded: String, precision: Double): List<LatLon> {
        val out = ArrayList<LatLon>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var shift = 0
            var result = 0L
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            out.add(LatLon(lat / precision, lon / precision))
        }
        return out
    }

    /** Metreyi Türkçe okunur hale getirir: 850 m, 1,2 km, 212 km. */
    fun formatDistance(meters: Double): String = when {
        meters < 0 -> "–"
        meters < 1000 -> "${(meters / 10).toInt() * 10} m"
        meters < 10_000 -> String.format(java.util.Locale.GERMANY, "%.1f km", meters / 1000)
        else -> "${(meters / 1000).toInt()} km"
    }

    /** Saniyeyi "2 sa 15 dk" biçiminde yazar. */
    fun formatDuration(seconds: Double): String {
        val m = (seconds / 60).toInt()
        return if (m < 60) "$m dk" else "${m / 60} sa ${m % 60} dk"
    }
}
