package com.technoral.ucusbul.core

import android.content.Context
import android.content.SharedPreferences
import com.technoral.ucusbul.data.GroundTransport

/**
 * Tüm kullanıcı ayarları ve API anahtarları cihazda saklanır; hiçbir sunucuya gönderilmez.
 * Anahtarlar uygulamanın içine gömülü değildir, kullanıcı Ayarlar ekranından girer.
 */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("ucusbul", Context.MODE_PRIVATE)

    var amadeusKey: String
        get() = sp.getString(K_AMADEUS_KEY, "").orEmpty()
        set(v) = sp.edit().putString(K_AMADEUS_KEY, v.trim()).apply()

    var amadeusSecret: String
        get() = sp.getString(K_AMADEUS_SECRET, "").orEmpty()
        set(v) = sp.edit().putString(K_AMADEUS_SECRET, v.trim()).apply()

    /** true ise api.amadeus.com (canlı), false ise test.api.amadeus.com kullanılır. */
    var amadeusProduction: Boolean
        get() = sp.getBoolean(K_AMADEUS_PROD, false)
        set(v) = sp.edit().putBoolean(K_AMADEUS_PROD, v).apply()

    var travelpayoutsToken: String
        get() = sp.getString(K_TP_TOKEN, "").orEmpty()
        set(v) = sp.edit().putString(K_TP_TOKEN, v.trim()).apply()

    var travelpayoutsMarker: String
        get() = sp.getString(K_TP_MARKER, "").orEmpty()
        set(v) = sp.edit().putString(K_TP_MARKER, v.trim()).apply()

    var currency: String
        get() = sp.getString(K_CURRENCY, "TRY").orEmpty().ifBlank { "TRY" }
        set(v) = sp.edit().putString(K_CURRENCY, v.trim().uppercase()).apply()

    var transport: GroundTransport
        get() = GroundTransport.fromName(sp.getString(K_TRANSPORT, null))
        set(v) = sp.edit().putString(K_TRANSPORT, v.name).apply()

    /** Hedef şehre en fazla kaç dakika kara mesafesindeki havalimanları taransın. */
    var maxGroundMinutes: Int
        get() = sp.getInt(K_GROUND_MIN, 120)
        set(v) = sp.edit().putInt(K_GROUND_MIN, v).apply()

    var includeSmallAirports: Boolean
        get() = sp.getBoolean(K_SMALL, false)
        set(v) = sp.edit().putBoolean(K_SMALL, v).apply()

    /** Kaç farklı varış havalimanı taransın (API kotasını korumak için sınırlı). */
    var maxDestinations: Int
        get() = sp.getInt(K_MAX_DEST, 6)
        set(v) = sp.edit().putInt(K_MAX_DEST, v).apply()

    /** Kalkış tarafında da yakın havalimanları taransın mı (ör. IST + SAW + Ankara). */
    var searchNearbyOrigins: Boolean
        get() = sp.getBoolean(K_ORIGIN_NEARBY, false)
        set(v) = sp.edit().putBoolean(K_ORIGIN_NEARBY, v).apply()

    var maxOrigins: Int
        get() = sp.getInt(K_MAX_ORIGIN, 2)
        set(v) = sp.edit().putInt(K_MAX_ORIGIN, v).apply()

    /** Hiç API anahtarı yoksa örnek verilerle çalışsın mı. */
    var demoFallback: Boolean
        get() = sp.getBoolean(K_DEMO, true)
        set(v) = sp.edit().putBoolean(K_DEMO, v).apply()

    var watchesJson: String
        get() = sp.getString(K_WATCHES, "[]").orEmpty()
        set(v) = sp.edit().putString(K_WATCHES, v).apply()

    var lastSearchJson: String
        get() = sp.getString(K_LAST_SEARCH, "").orEmpty()
        set(v) = sp.edit().putString(K_LAST_SEARCH, v).apply()

    val hasAnyProvider: Boolean
        get() = (amadeusKey.isNotBlank() && amadeusSecret.isNotBlank()) || travelpayoutsToken.isNotBlank()

    companion object {
        private const val K_AMADEUS_KEY = "amadeus_key"
        private const val K_AMADEUS_SECRET = "amadeus_secret"
        private const val K_AMADEUS_PROD = "amadeus_prod"
        private const val K_TP_TOKEN = "tp_token"
        private const val K_TP_MARKER = "tp_marker"
        private const val K_CURRENCY = "currency"
        private const val K_TRANSPORT = "transport"
        private const val K_GROUND_MIN = "ground_minutes"
        private const val K_SMALL = "include_small"
        private const val K_MAX_DEST = "max_destinations"
        private const val K_ORIGIN_NEARBY = "origin_nearby"
        private const val K_MAX_ORIGIN = "max_origins"
        private const val K_DEMO = "demo_fallback"
        private const val K_WATCHES = "watches"
        private const val K_LAST_SEARCH = "last_search"

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
    }
}
