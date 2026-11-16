package com.teklif.yolcu.nav

import com.teklif.yolcu.geo.LatLon

/** Aranıp bulunan yer. */
data class Place(val name: String, val detail: String, val position: LatLon)

/** Dönüş simgesi türü (telefon ekranı ve Android Auto ortak). */
enum class TurnType {
    DEPART, ARRIVE, STRAIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, LEFT, RIGHT, SHARP_LEFT, SHARP_RIGHT,
    UTURN_LEFT, UTURN_RIGHT, RAMP_LEFT, RAMP_RIGHT, EXIT_LEFT, EXIT_RIGHT, KEEP_LEFT, KEEP_RIGHT,
    MERGE, ROUNDABOUT, ROUNDABOUT_EXIT, FERRY, UNKNOWN,
}

/** Rotadaki bir manevra (dönüş). */
data class Maneuver(
    val type: TurnType,
    /** Ekranda gösterilen Türkçe talimat. */
    val instruction: String,
    /** Erken uyarı metni (ör. "sağa dönün"); yoksa [instruction]. */
    val verbalAlert: String,
    /** Manevra yaklaşınca okunan metin. */
    val verbalPre: String,
    val streetName: String,
    /** Manevranın başladığı şekil noktası. */
    val beginShapeIndex: Int,
    /** Bu manevradan sonrakine kadar yol (m) ve süre (s). */
    val lengthM: Double,
    val timeS: Double,
    val roundaboutExit: Int = 0,
)

/** Rota üzerinde ülke değişimi. */
data class BorderEvent(
    val shapeIndex: Int,
    val distanceAlongM: Double,
    val fromIso: String,
    val fromName: String,
    val toIso: String,
    val toName: String,
    /** Bilinen en yakın sınır kapısının adı, yoksa null. */
    val crossingName: String?,
    val point: LatLon,
    val schengenInternal: Boolean,
)

data class Route(
    val shape: List<LatLon>,
    /** Her şekil noktasının rota başından uzaklığı (m). */
    val cumulative: DoubleArray,
    val maneuvers: List<Maneuver>,
    val lengthM: Double,
    val timeS: Double,
    val destination: Place,
    val borders: List<BorderEvent> = emptyList(),
    val provider: String,
)

/** Navigasyon sırasında anlık ilerleme. */
data class Progress(
    val shapeIndex: Int,
    val distanceAlongM: Double,
    val remainingM: Double,
    val remainingS: Double,
    val nextManeuver: Maneuver?,
    val distanceToManeuverM: Double,
    val nextBorder: BorderEvent?,
    val distanceToBorderM: Double,
    val offRoute: Boolean,
    /** Konumun rota çizgisi üzerine oturtulmuş hali (rotadayken ok yolun üstünde dursun). */
    val snapped: LatLon,
    /** Bulunulan rota parçasının yönü (derece). */
    val routeBearing: Double,
)

data class CountryInfo(val iso: String, val name: String, val flag: String)

/** Kilometre sayacı. */
data class TripStats(
    val totalM: Double = 0.0,
    val byCountryM: Map<String, Double> = emptyMap(),
    /** Şu anki ülkeye girişten beri (m). */
    val sinceBorderM: Double = 0.0,
    val startedAt: Long = 0L,
)

/** Haritada gösterilecek konum ve yön: rotadayken rota üzerine oturtulmuş. */
val NavSnapshot.displayPosition: LatLon? get() = progress?.takeIf { navigating && !it.offRoute }?.snapped ?: location
val NavSnapshot.displayHeading: Float? get() = progress?.takeIf { navigating && !it.offRoute }?.routeBearing?.toFloat() ?: heading

data class NavSnapshot(
    val location: LatLon? = null,
    val speedKmh: Double = 0.0,
    val heading: Float? = null,
    val accuracyM: Float = 0f,
    val country: CountryInfo? = null,
    val route: Route? = null,
    val progress: Progress? = null,
    val navigating: Boolean = false,
    val rerouting: Boolean = false,
    val routing: Boolean = false,
    val trip: TripStats = TripStats(),
    val message: String? = null,
    val gpsReady: Boolean = false,
)
