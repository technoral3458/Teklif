package com.teklif.yolcu.nav

import com.teklif.yolcu.geo.Geo
import com.teklif.yolcu.geo.LatLon
import kotlin.math.max
import kotlin.math.min

/**
 * Konumu rotaya oturtur, sıradaki manevrayı/sınırı bulur, sesli uyarıları zamanlar,
 * rotadan çıkıldığını fark eder.
 */
class NavigationEngine(private val speak: (String) -> Unit) {

    private var route: Route? = null
    private var lastIndex = 0
    private var ptr = 0
    private var farAnnounced = -1
    private var nearAnnounced = -1
    private var borderAnnounced = HashMap<Int, Int>() // sınır indeksi -> duyurulan eşik
    private var offRouteSince = 0L
    private var rerouteCooldownUntil = 0L
    private var lastGlobalSearch = 0L
    private var arrived = false

    var onOffRoute: (() -> Unit)? = null
    var onArrived: (() -> Unit)? = null

    fun setRoute(r: Route?) {
        route = r
        lastIndex = 0
        ptr = 0
        farAnnounced = -1
        nearAnnounced = -1
        borderAnnounced = HashMap()
        offRouteSince = 0L
        arrived = false
    }

    fun update(p: LatLon, speedMs: Float, accuracyM: Float, now: Long): Progress? {
        val r = route ?: return null
        val shape = r.shape
        val cum = r.cumulative
        val n = shape.size

        // En yakın parçayı bul: önce son konumun çevresinde, gerekirse tüm rotada.
        var (bestI, bestT, bestD) = nearest(p, shape, max(0, lastIndex - 10), min(n - 2, lastIndex + 400))
        // Pencere dışına düşüldüyse (tünel çıkışı, uzun süre sonra devam) tüm rotada ara.
        if (bestD > 60 && (bestD > 500 || now - lastGlobalSearch > 5000)) {
            lastGlobalSearch = now
            val g = nearest(p, shape, 0, n - 2)
            if (g.third < bestD) { bestI = g.first; bestT = g.second; bestD = g.third }
        }
        lastIndex = bestI
        val along = cum[bestI] + bestT * (cum[bestI + 1] - cum[bestI])
        val total = cum[n - 1]
        val remaining = max(0.0, total - along)

        // Sıradaki manevra
        val ms = r.maneuvers
        while (ptr < ms.size - 1 && cum[ms[ptr].beginShapeIndex.coerceIn(0, n - 1)] <= along - 15) ptr++
        if (ptr < ms.size - 1 && ms[ptr].type == TurnType.DEPART) ptr++
        val next = ms.getOrNull(ptr)
        val distToMan = next?.let { max(0.0, cum[it.beginShapeIndex.coerceIn(0, n - 1)] - along) } ?: remaining

        // Kalan süre: kalan manevraların süresi, içinde bulunulanın kalan oranıyla.
        var remainingS = 0.0
        if (next != null) {
            val curIdx = max(0, ptr - 1)
            val cur = ms[curIdx]
            val curStart = cum[cur.beginShapeIndex.coerceIn(0, n - 1)]
            val frac = if (cur.lengthM > 0) ((along - curStart) / cur.lengthM).coerceIn(0.0, 1.0) else 1.0
            remainingS = cur.timeS * (1 - frac)
            for (k in ptr until ms.size) remainingS += ms[k].timeS
        }
        if (remainingS <= 0 && total > 0) remainingS = r.timeS * remaining / total

        // Sıradaki sınır
        val border = r.borders.firstOrNull { it.distanceAlongM > along - 100 }
        val distToBorder = border?.let { max(0.0, it.distanceAlongM - along) } ?: -1.0

        // Rotadan çıkma
        val tolerance = 45.0 + accuracyM.coerceIn(0f, 60f)
        val offRoute = bestD > tolerance
        if (offRoute) {
            if (offRouteSince == 0L) offRouteSince = now
            if (now - offRouteSince > 6000 && speedMs > 1.5f && now > rerouteCooldownUntil) {
                rerouteCooldownUntil = now + 25_000
                offRouteSince = 0L
                onOffRoute?.invoke()
            }
        } else {
            offRouteSince = 0L
        }

        if (!offRoute) {
            announceManeuver(next, distToMan, speedMs)
            announceBorder(border, distToBorder)
            if (!arrived && remaining < 40 && Geo.distance(p, r.destination.position) < 80) {
                arrived = true
                speak("Hedefinize vardınız.")
                onArrived?.invoke()
            }
        }

        val (snapped, _) = Geo.projectOnSegment(p, shape[bestI], shape[bestI + 1])
        return Progress(
            shapeIndex = bestI,
            distanceAlongM = along,
            remainingM = remaining,
            remainingS = remainingS,
            nextManeuver = next,
            distanceToManeuverM = distToMan,
            nextBorder = border,
            distanceToBorderM = distToBorder,
            offRoute = offRoute,
            snapped = snapped,
            routeBearing = Geo.bearing(shape[bestI], shape[bestI + 1]),
        )
    }

    private fun nearest(p: LatLon, shape: List<LatLon>, from: Int, to: Int): Triple<Int, Double, Double> {
        var bestI = from
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        var i = from
        while (i <= to) {
            val (proj, t) = Geo.projectOnSegment(p, shape[i], shape[i + 1])
            val d = Geo.distance(p, proj)
            if (d < bestD) { bestD = d; bestI = i; bestT = t }
            i++
        }
        return Triple(bestI, bestT, bestD)
    }

    private fun announceManeuver(next: Maneuver?, dist: Double, speedMs: Float) {
        if (next == null) return
        val far = when {
            speedMs > 25 -> 1500.0
            speedMs > 14 -> 700.0
            else -> 250.0
        }
        val near = when {
            speedMs > 25 -> 350.0
            speedMs > 14 -> 150.0
            else -> 50.0
        }
        if (dist <= near) {
            if (nearAnnounced != ptr) {
                nearAnnounced = ptr
                farAnnounced = ptr
                speak(next.verbalPre.trimEnd('.'))
            }
        } else if (dist <= far && farAnnounced != ptr) {
            farAnnounced = ptr
            speak("${spokenDistance(dist)} sonra ${next.verbalAlert.trimEnd('.')}")
        }
    }

    private fun announceBorder(border: BorderEvent?, dist: Double) {
        if (border == null || dist < 0) return
        val key = border.shapeIndex
        val done = borderAnnounced[key] ?: Int.MAX_VALUE
        val threshold = when {
            dist <= 1_200 -> 1
            dist <= 5_500 -> 5
            dist <= 21_000 -> 20
            else -> return
        }
        if (threshold < done) {
            borderAnnounced[key] = threshold
            val gate = border.crossingName?.let { " Sınır kapısı: $it." } ?: ""
            val kontrol = if (border.schengenInternal) " Schengen içi, kontrol yok." else ""
            speak("Yaklaşık ${threshold} kilometre sonra ${border.toName} sınırı.$gate$kontrol")
        }
    }

    companion object {
        /** Sesli okunacak mesafe: "800 metre", "1,5 kilometre", "12 kilometre". */
        fun spokenDistance(m: Double): String = when {
            m < 950 -> "${((m + 25) / 50).toInt() * 50} metre"
            m < 9_500 -> String.format(java.util.Locale.GERMANY, "%.1f kilometre", m / 1000).replace(",0", "")
            else -> "${(m / 1000).toInt()} kilometre"
        }
    }
}
