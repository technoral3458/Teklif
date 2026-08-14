package com.technoral.ucusbul.work

import android.content.Context
import com.technoral.ucusbul.core.Prefs
import com.technoral.ucusbul.data.GroundTransport
import com.technoral.ucusbul.data.SearchQuery
import org.json.JSONArray
import org.json.JSONObject

/** Kullanıcının takibe aldığı arama. Fiyat düşünce bildirim gelir. */
data class Watch(
    val id: String,
    val label: String,
    val query: SearchQuery,
    val targetPrice: Double?,
    val lastBestPrice: Double? = null,
    val lastCheckedAt: Long = 0L,
    val lastNote: String = ""
)

/** Takip listesini SharedPreferences içinde JSON olarak saklar. */
class WatchStore(context: Context) {

    private val prefs = Prefs.get(context)

    fun all(): List<Watch> {
        val out = ArrayList<Watch>()
        val arr = runCatching { JSONArray(prefs.watchesJson) }.getOrElse { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            runCatching { out.add(fromJson(o)) }
        }
        return out
    }

    fun save(list: List<Watch>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.watchesJson = arr.toString()
    }

    fun add(watch: Watch) {
        val list = all().filterNot { it.id == watch.id } + watch
        save(list)
    }

    fun remove(id: String) = save(all().filterNot { it.id == id })

    fun update(id: String, transform: (Watch) -> Watch) {
        save(all().map { if (it.id == id) transform(it) else it })
    }

    companion object {

        fun toJson(w: Watch): JSONObject = JSONObject().apply {
            put("id", w.id)
            put("label", w.label)
            put("query", queryToJson(w.query))
            w.targetPrice?.let { put("target", it) }
            w.lastBestPrice?.let { put("lastBest", it) }
            put("lastChecked", w.lastCheckedAt)
            put("lastNote", w.lastNote)
        }

        fun fromJson(o: JSONObject): Watch = Watch(
            id = o.getString("id"),
            label = o.optString("label"),
            query = queryFromJson(o.getJSONObject("query")),
            targetPrice = if (o.has("target")) o.getDouble("target") else null,
            lastBestPrice = if (o.has("lastBest")) o.getDouble("lastBest") else null,
            lastCheckedAt = o.optLong("lastChecked", 0L),
            lastNote = o.optString("lastNote")
        )

        fun queryToJson(q: SearchQuery): JSONObject = JSONObject().apply {
            put("originIata", q.originIata)
            put("originCity", q.originCity)
            put("destinationIata", q.destinationIata)
            put("destinationCity", q.destinationCity)
            put("departureDate", q.departureDate)
            q.returnDate?.let { put("returnDate", it) }
            put("adults", q.adults)
            put("maxGroundMinutes", q.maxGroundMinutes)
            put("transport", q.transport.name)
            put("includeSmall", q.includeSmallAirports)
            put("maxDestinations", q.maxDestinations)
            put("nearbyOrigins", q.searchNearbyOrigins)
            put("nonStopOnly", q.nonStopOnly)
        }

        fun queryFromJson(o: JSONObject): SearchQuery = SearchQuery(
            originIata = o.getString("originIata"),
            originCity = o.optString("originCity"),
            destinationIata = o.getString("destinationIata"),
            destinationCity = o.optString("destinationCity"),
            departureDate = o.getString("departureDate"),
            returnDate = if (o.has("returnDate")) o.getString("returnDate") else null,
            adults = o.optInt("adults", 1),
            maxGroundMinutes = o.optInt("maxGroundMinutes", 120),
            transport = GroundTransport.fromName(o.optString("transport")),
            includeSmallAirports = o.optBoolean("includeSmall", false),
            maxDestinations = o.optInt("maxDestinations", 6),
            searchNearbyOrigins = o.optBoolean("nearbyOrigins", false),
            nonStopOnly = o.optBoolean("nonStopOnly", false)
        )
    }
}
