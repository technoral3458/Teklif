package com.teklif.yolcu.routing

import com.teklif.yolcu.geo.LatLon
import com.teklif.yolcu.map.TileCache
import com.teklif.yolcu.nav.Place
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Adres / yer arama (Photon, OpenStreetMap verisi, ücretsiz). */
object Geocoder {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * @param near sonuçları bu konuma yakın olanlara öncelik verir
     */
    fun search(query: String, near: LatLon?, limit: Int = 8): List<Place> {
        val url = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("lang", "en")
            .apply {
                if (near != null) {
                    addQueryParameter("lat", near.lat.toString())
                    addQueryParameter("lon", near.lon.toString())
                }
            }
            .build()
        val request = Request.Builder().url(url).header("User-Agent", TileCache.USER_AGENT).build()
        val text = http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            r.body?.string().orEmpty()
        }
        val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val p = f.getJSONObject("properties")
            val c = f.getJSONObject("geometry").getJSONArray("coordinates")
            val name = p.optString("name").ifBlank {
                listOf(p.optString("street"), p.optString("housenumber")).filter { it.isNotBlank() }.joinToString(" ")
            }.ifBlank { query }
            val detail = listOf(
                listOf(p.optString("street"), p.optString("housenumber")).filter { it.isNotBlank() }.joinToString(" ")
                    .takeIf { it.isNotBlank() && it != name },
                p.optString("district").takeIf { it.isNotBlank() && it != name },
                p.optString("city").takeIf { it.isNotBlank() && it != name },
                p.optString("state").takeIf { it.isNotBlank() && it != p.optString("city") },
                countryTr(p.optString("countrycode"), p.optString("country")),
            ).filterNotNull().filter { it.isNotBlank() }.distinct().joinToString(", ")
            out.add(Place(name, detail, LatLon(c.getDouble(1), c.getDouble(0))))
        }
        return out
    }

    private fun countryTr(code: String, fallback: String): String =
        com.teklif.yolcu.geo.Countries.TURKISH_NAMES[code.uppercase()] ?: fallback
}
