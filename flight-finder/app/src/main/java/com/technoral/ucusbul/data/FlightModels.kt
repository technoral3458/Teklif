package com.technoral.ucusbul.data

/** Uçuşun tek bacağı. */
data class FlightSegment(
    val carrierCode: String,
    val flightNumber: String,
    val from: String,
    val to: String,
    val departure: String,
    val arrival: String
)

/** Bir sağlayıcıdan gelen tek bir fiyat teklifi. */
data class FlightOffer(
    val id: String,
    val provider: String,
    val origin: String,
    val destination: String,
    val price: Double,
    val currency: String,
    val airlineCodes: List<String>,
    val stops: Int,
    val durationMinutes: Int,
    val departure: String,
    val arrival: String,
    val segments: List<FlightSegment> = emptyList(),
    /** Sağlayıcı doğrudan satın alma linki verdiyse burada. */
    val deepLink: String? = null,
    val isDemo: Boolean = false
) {
    val mainAirline: String get() = airlineCodes.firstOrNull().orEmpty()
}

/** Teklif + hangi "yakın havalimanına" ait olduğu bilgisi. */
data class OfferResult(
    val offer: FlightOffer,
    val destination: NearbyAirport,
    val originAirport: Airport
) {
    /** Uçuş süresi + hedef şehre kara yolculuğu. */
    val totalMinutes: Int get() = offer.durationMinutes + destination.groundMinutes
}

/** Tek bir kalkış-varış çifti için sağlayıcıya gönderilen sorgu. */
data class LegQuery(
    val origin: String,
    val destination: String,
    val departureDate: String,
    val returnDate: String? = null,
    val adults: Int = 1,
    val currency: String = "TRY",
    val nonStopOnly: Boolean = false,
    val maxResults: Int = 12,
    /** Sadece örnek (demo) sağlayıcının gerçekçi fiyat üretmesi için kullanılır. */
    val distanceKm: Double = 0.0
)

/** Kullanıcının ekranda kurduğu arama. */
data class SearchQuery(
    val originIata: String,
    val originCity: String,
    val destinationIata: String,
    val destinationCity: String,
    val departureDate: String,
    val returnDate: String?,
    val adults: Int,
    val maxGroundMinutes: Int,
    val transport: GroundTransport,
    val includeSmallAirports: Boolean,
    val maxDestinations: Int,
    val searchNearbyOrigins: Boolean,
    val nonStopOnly: Boolean
)

enum class SortMode(val title: String) {
    PRICE("En ucuz"),
    TOTAL_TIME("En kısa toplam süre"),
    SMART("Akıllı (fiyat + süre)")
}

/** Arama sonucu + hangi havalimanlarının tarandığı ve sağlayıcı hataları. */
data class SearchOutcome(
    val results: List<OfferResult> = emptyList(),
    val scanned: List<NearbyAirport> = emptyList(),
    val notes: List<String> = emptyList(),
    val usedDemo: Boolean = false
)
