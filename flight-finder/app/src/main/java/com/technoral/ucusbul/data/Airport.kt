package com.technoral.ucusbul.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tek bir havalimanı kaydı. Veri seti uygulama içine gömülüdür (assets/airports.json),
 * bu yüzden havalimanı arama ve "yakın havalimanı" hesabı internet olmadan da çalışır.
 */
data class Airport(
    val iata: String,
    val name: String,
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    /** 0 = büyük, 1 = orta, 2 = küçük havalimanı */
    val rank: Int,
    /** Veri setindeki ham şehir alanı (ör. "Arnavutköy, Istanbul") - sadece arama için */
    val altCity: String
) {
    val label: String get() = "$city ($iata)"
    val isMajor: Boolean get() = rank == 0
}

/** Kullanıcının seçtiği ulaşım tipi - "2 saat mesafe" bunun üzerinden hesaplanır. */
enum class GroundTransport(val title: String, val speedKmh: Double, val overheadMin: Int) {
    ROAD("Karayolu / otobüs", 80.0, 30),
    MIXED("Karışık (yol + tren)", 120.0, 35),
    HIGH_SPEED_RAIL("Hızlı tren", 200.0, 45);

    companion object {
        fun fromName(name: String?): GroundTransport =
            entries.firstOrNull { it.name == name } ?: MIXED
    }
}

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    /** İki nokta arasındaki kuş uçuşu mesafe (km). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /**
     * Kuş uçuşu mesafeyi gerçekçi bir kara yolculuğuna çevirir: yollar düz gitmediği için
     * 1.25 katsayısı, ayrıca gara/otogara varış + bekleme için sabit bir ek süre eklenir.
     */
    fun groundMinutes(distanceKm: Double, transport: GroundTransport): Int {
        if (distanceKm < 3.0) return 0
        val routeKm = distanceKm * 1.25
        return (routeKm / transport.speedKmh * 60.0).roundToInt() + transport.overheadMin
    }
}

/** Hedef havalimanı + ana şehre olan kara mesafesi. */
data class NearbyAirport(
    val airport: Airport,
    val distanceKm: Int,
    val groundMinutes: Int,
    val isAnchor: Boolean
)

fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0 dk"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m dk"
        m == 0 -> "$h sa"
        else -> "$h sa $m dk"
    }
}
