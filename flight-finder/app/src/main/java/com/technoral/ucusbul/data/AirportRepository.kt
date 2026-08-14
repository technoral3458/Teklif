package com.technoral.ucusbul.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Gömülü havalimanı veri setini okur; şehir/havalimanı araması ve
 * "hedefe X saat mesafedeki havalimanları" hesabını sağlar.
 */
class AirportRepository private constructor(private val appContext: Context) {

    private val mutex = Mutex()
    @Volatile private var airports: List<Airport> = emptyList()
    private var byIata: Map<String, Airport> = emptyMap()

    suspend fun all(): List<Airport> {
        airports.takeIf { it.isNotEmpty() }?.let { return it }
        return mutex.withLock {
            airports.takeIf { it.isNotEmpty() } ?: withContext(Dispatchers.IO) {
                val parsed = parse()
                airports = parsed
                byIata = parsed.associateBy { it.iata }
                parsed
            }
        }
    }

    suspend fun byIata(iata: String): Airport? {
        all()
        return byIata[iata.uppercase(Locale.ROOT)]
    }

    private fun parse(): List<Airport> {
        val text = appContext.assets.open("airports.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val arr: JSONArray = root.getJSONArray("a")
        val out = ArrayList<Airport>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONArray(i)
            out.add(
                Airport(
                    iata = r.getString(0),
                    name = r.getString(1),
                    city = r.getString(2),
                    country = r.getString(3),
                    lat = r.getDouble(4),
                    lon = r.getDouble(5),
                    rank = r.getInt(6),
                    altCity = r.optString(7, "")
                )
            )
        }
        return out
    }

    /** Serbest metin araması: "jinan", "TNA", "istanbul", "cinan" hepsi çalışır. */
    suspend fun search(query: String, limit: Int = 25): List<Airport> {
        val list = all()
        val q = normalize(query)
        if (q.length < 2) return emptyList()
        val expanded = CityAliases.expand(q)

        val scored = ArrayList<Pair<Int, Airport>>()
        for (a in list) {
            val score = score(a, q, expanded)
            if (score > 0) scored.add(score to a)
        }
        return scored
            .sortedWith(compareByDescending<Pair<Int, Airport>> { it.first }.thenBy { it.second.rank })
            .take(limit)
            .map { it.second }
    }

    private fun score(a: Airport, q: String, expanded: Set<String>): Int {
        val city = normalize(a.city)
        val name = normalize(a.name)
        val alt = normalize(a.altCity)
        val rankBonus = when (a.rank) {
            0 -> 30
            1 -> 12
            else -> 0
        }
        // IATA kodu birebir - en güçlü eşleşme
        if (q.length == 3 && normalize(a.iata) == q) return 1000 + rankBonus
        for (term in expanded) {
            if (city == term) return 600 + rankBonus
            if (alt == term) return 560 + rankBonus
        }
        for (term in expanded) {
            if (city.startsWith(term)) return 400 + rankBonus
            if (alt.startsWith(term)) return 360 + rankBonus
            if (name.startsWith(term)) return 340 + rankBonus
        }
        for (term in expanded) {
            if (city.contains(term)) return 200 + rankBonus
            if (name.contains(term)) return 150 + rankBonus
            if (alt.contains(term)) return 140 + rankBonus
        }
        return 0
    }

    /**
     * Bir aramanın gideceği/kalkacağı "şehir" havalimanlarını verir.
     * İstanbul araması IST + SAW döndürür; böylece iki havalimanı da taranır.
     */
    suspend fun cityAirports(anchor: Airport, maxExtraKm: Double = 60.0): List<Airport> {
        val list = all()
        val same = list.filter { a ->
            a.iata == anchor.iata || (
                a.country == anchor.country &&
                    normalize(a.city) == normalize(anchor.city) &&
                    Geo.distanceKm(anchor.lat, anchor.lon, a.lat, a.lon) <= maxExtraKm
                )
        }
        return same.sortedWith(compareBy({ it.rank }, { it.iata }))
    }

    /**
     * Hedef şehre en fazla [maxGroundMinutes] kara mesafesinde olan havalimanları.
     * Ana havalimanı her zaman listenin başındadır.
     */
    suspend fun nearby(
        anchor: Airport,
        maxGroundMinutes: Int,
        transport: GroundTransport,
        includeSmall: Boolean,
        limit: Int
    ): List<NearbyAirport> {
        val list = all()
        val anchorCity = cityAirports(anchor).map { it.iata }.toSet()

        val out = ArrayList<NearbyAirport>()
        for (a in list) {
            if (!includeSmall && a.rank > 1) continue
            val d = Geo.distanceKm(anchor.lat, anchor.lon, a.lat, a.lon)
            val minutes = Geo.groundMinutes(d, transport)
            val isAnchor = a.iata in anchorCity
            if (!isAnchor && minutes > maxGroundMinutes) continue
            out.add(NearbyAirport(a, d.toInt(), if (isAnchor) 0 else minutes, isAnchor))
        }
        // Önce ana şehir, sonra yakınlık; eşitlikte büyük havalimanı önde.
        return out
            .sortedWith(
                compareByDescending<NearbyAirport> { it.isAnchor }
                    .thenBy { it.groundMinutes }
                    .thenBy { it.airport.rank }
            )
            .take(limit)
    }

    companion object {
        @Volatile private var instance: AirportRepository? = null

        fun get(context: Context): AirportRepository =
            instance ?: synchronized(this) {
                instance ?: AirportRepository(context.applicationContext).also { instance = it }
            }

        /** Türkçe karakterleri ve büyük/küçük harfi önemsemeyen normalizasyon. */
        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s.lowercase(Locale.ROOT)) {
                val c = when (ch) {
                    'ı', 'î', 'í' -> 'i'
                    'ş' -> 's'
                    'ğ' -> 'g'
                    'ü', 'û', 'ú' -> 'u'
                    'ö', 'ô', 'ó' -> 'o'
                    'ç' -> 'c'
                    'â', 'á', 'ä', 'à' -> 'a'
                    'é', 'è', 'ê' -> 'e'
                    '\'', '’', '-', '.', ',', '/' -> ' '
                    else -> ch
                }
                sb.append(c)
            }
            return sb.toString().replace(Regex("\\s+"), " ").trim()
        }
    }
}
