package com.technoral.ucusbul.data.provider

import com.technoral.ucusbul.data.FlightOffer
import com.technoral.ucusbul.data.LegQuery

/**
 * Uygulama tek bir bilet sitesine bağlı değildir: her sağlayıcı ayrı bir kaynaktır ve
 * sonuçlar birleştirilip birlikte sıralanır. Yeni bir kaynak eklemek için bu arayüzü
 * uygulayıp [Providers] içine kaydetmek yeterlidir.
 */
interface FlightProvider {
    val id: String
    val displayName: String

    /** Gerekli anahtarlar girilmiş mi. */
    fun isConfigured(): Boolean

    /** Tek bir kalkış-varış çifti için teklifleri getirir. */
    suspend fun search(query: LegQuery): List<FlightOffer>
}
