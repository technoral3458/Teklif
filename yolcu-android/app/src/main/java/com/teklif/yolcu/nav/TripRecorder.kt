package com.teklif.yolcu.nav

import android.content.Context
import com.teklif.yolcu.geo.Geo
import com.teklif.yolcu.geo.LatLon
import org.json.JSONObject

/** Gidilen yolu sayar: toplam, ülke ülke ve son sınırdan beri. Telefona kaydedilir. */
class TripRecorder(context: Context) {

    private val prefs = context.getSharedPreferences("trip", Context.MODE_PRIVATE)
    private var last: LatLon? = null
    private var lastTime = 0L
    private var lastSave = 0L

    var stats: TripStats = load()
        private set

    /**
     * @param accuracyM GPS doğruluğu; kötü ölçümler sayılmaz
     * @param speedMs hız; dururken GPS titremesi sayılmaz
     */
    fun onLocation(p: LatLon, accuracyM: Float, speedMs: Float, countryIso: String?, now: Long = System.currentTimeMillis()) {
        val prev = last
        if (accuracyM > 50f) return
        if (prev == null) {
            last = p; lastTime = now
            if (stats.startedAt == 0L) stats = stats.copy(startedAt = now)
            return
        }
        val d = Geo.distance(prev, p)
        val dt = (now - lastTime) / 1000.0
        last = p; lastTime = now
        // Sıçrama (GPS hatası) ya da yerinde titreme sayılmaz.
        if (d < 2.0 || (dt > 0 && d / dt > 70) || (speedMs < 0.8f && d < 15)) return
        val by = stats.byCountryM.toMutableMap()
        if (countryIso != null) by[countryIso] = (by[countryIso] ?: 0.0) + d
        stats = stats.copy(totalM = stats.totalM + d, byCountryM = by, sinceBorderM = stats.sinceBorderM + d)
        if (now - lastSave > 20_000) save(now)
    }

    /** Yeni ülkeye girildi: ülke içi sayaç sıfırlanır. */
    fun onBorderCrossed() {
        stats = stats.copy(sinceBorderM = 0.0)
        save(System.currentTimeMillis())
    }

    fun reset() {
        stats = TripStats(startedAt = System.currentTimeMillis())
        save(System.currentTimeMillis())
    }

    fun save(now: Long = System.currentTimeMillis()) {
        lastSave = now
        val by = JSONObject()
        stats.byCountryM.forEach { (k, v) -> by.put(k, v) }
        prefs.edit()
            .putFloat("total", stats.totalM.toFloat())
            .putFloat("sinceBorder", stats.sinceBorderM.toFloat())
            .putLong("startedAt", stats.startedAt)
            .putString("byCountry", by.toString())
            .apply()
    }

    private fun load(): TripStats {
        val by = HashMap<String, Double>()
        runCatching {
            val o = JSONObject(prefs.getString("byCountry", "{}") ?: "{}")
            o.keys().forEach { by[it] = o.getDouble(it) }
        }
        return TripStats(
            totalM = prefs.getFloat("total", 0f).toDouble(),
            byCountryM = by,
            sinceBorderM = prefs.getFloat("sinceBorder", 0f).toDouble(),
            startedAt = prefs.getLong("startedAt", 0L),
        )
    }
}
