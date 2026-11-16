package com.teklif.yolcu.geo

import android.content.Context
import org.json.JSONObject

/** Bir ülkenin sınır çokgenleri (Natural Earth 1:50m, Avrupa alt kümesi). */
class Country(val iso2: String, val nameTr: String, private val rings: List<Ring>) {

    /** Dış halka + içindeki delikler ve sınırlayıcı kutu. */
    class Ring(val outer: DoubleArray, val holes: List<DoubleArray>) {
        val minLon: Double
        val maxLon: Double
        val minLat: Double
        val maxLat: Double

        init {
            var a = Double.MAX_VALUE
            var b = -Double.MAX_VALUE
            var c = Double.MAX_VALUE
            var d = -Double.MAX_VALUE
            var i = 0
            while (i < outer.size) {
                val lon = outer[i]
                val lat = outer[i + 1]
                if (lon < a) a = lon
                if (lon > b) b = lon
                if (lat < c) c = lat
                if (lat > d) d = lat
                i += 2
            }
            minLon = a; maxLon = b; minLat = c; maxLat = d
        }

        fun contains(lat: Double, lon: Double): Boolean {
            if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false
            if (!Geo.pointInRing(lat, lon, outer)) return false
            return holes.none { Geo.pointInRing(lat, lon, it) }
        }
    }

    fun contains(lat: Double, lon: Double): Boolean = rings.any { it.contains(lat, lon) }

    val flag: String get() = iso2.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

/** Ülke sınırlarını yükler ve bir noktanın hangi ülkede olduğunu bulur. */
class Countries private constructor(private val list: List<Country>) {

    @Volatile private var lastHit: Country? = null

    /** Noktanın ülkesi; sınır verisi dışındaysa null. Son bulunan ülke önce denenir (çok hızlı). */
    fun at(lat: Double, lon: Double): Country? {
        lastHit?.let { if (it.contains(lat, lon)) return it }
        val hit = list.firstOrNull { it.contains(lat, lon) }
        if (hit != null) lastHit = hit
        return hit
    }

    fun byIso(iso2: String): Country? = list.firstOrNull { it.iso2 == iso2 }

    companion object {
        @Volatile private var instance: Countries? = null

        fun get(context: Context): Countries = instance ?: synchronized(this) {
            instance ?: load(context).also { instance = it }
        }

        private fun load(context: Context): Countries {
            val text = context.assets.open("countries.geojson").bufferedReader().readText()
            val features = JSONObject(text).getJSONArray("features")
            val out = ArrayList<Country>()
            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val iso2 = f.getJSONObject("properties").getString("iso2")
                val geom = f.getJSONObject("geometry")
                val polys = if (geom.getString("type") == "Polygon") {
                    listOf(geom.getJSONArray("coordinates"))
                } else {
                    val mp = geom.getJSONArray("coordinates")
                    (0 until mp.length()).map { mp.getJSONArray(it) }
                }
                val rings = polys.map { poly ->
                    val outer = toArray(poly.getJSONArray(0))
                    val holes = (1 until poly.length()).map { toArray(poly.getJSONArray(it)) }
                    Country.Ring(outer, holes)
                }
                out.add(Country(iso2, TURKISH_NAMES[iso2] ?: iso2, rings))
            }
            // Küçük ülkeler önce: büyük komşularının kutusu içinde kalıyorlar.
            out.sortBy { c -> c.iso2 !in SMALL }
            return Countries(out)
        }

        private fun toArray(ring: org.json.JSONArray): DoubleArray {
            val arr = DoubleArray(ring.length() * 2)
            for (i in 0 until ring.length()) {
                val c = ring.getJSONArray(i)
                arr[i * 2] = c.getDouble(0)
                arr[i * 2 + 1] = c.getDouble(1)
            }
            return arr
        }

        private val SMALL = setOf("VA", "SM", "MC", "LI", "AD", "LU", "MT", "XK", "ME", "CY")

        val TURKISH_NAMES = mapOf(
            "TR" to "Türkiye", "BG" to "Bulgaristan", "GR" to "Yunanistan", "RO" to "Romanya", "RS" to "Sırbistan",
            "MK" to "Kuzey Makedonya", "AL" to "Arnavutluk", "ME" to "Karadağ", "BA" to "Bosna-Hersek", "HR" to "Hırvatistan",
            "SI" to "Slovenya", "HU" to "Macaristan", "AT" to "Avusturya", "IT" to "İtalya", "CH" to "İsviçre",
            "DE" to "Almanya", "CZ" to "Çekya", "SK" to "Slovakya", "PL" to "Polonya", "UA" to "Ukrayna", "MD" to "Moldova",
            "FR" to "Fransa", "BE" to "Belçika", "NL" to "Hollanda", "LU" to "Lüksemburg", "ES" to "İspanya", "PT" to "Portekiz",
            "GB" to "Birleşik Krallık", "IE" to "İrlanda", "DK" to "Danimarka", "SE" to "İsveç", "NO" to "Norveç",
            "FI" to "Finlandiya", "EE" to "Estonya", "LV" to "Letonya", "LT" to "Litvanya", "BY" to "Belarus", "RU" to "Rusya",
            "GE" to "Gürcistan", "AM" to "Ermenistan", "AZ" to "Azerbaycan", "CY" to "Kıbrıs", "MT" to "Malta",
            "LI" to "Lihtenştayn", "MC" to "Monako", "SM" to "San Marino", "VA" to "Vatikan", "AD" to "Andorra",
            "XK" to "Kosova", "IS" to "İzlanda", "SY" to "Suriye", "IQ" to "Irak", "IR" to "İran",
        )

        /** Schengen alanı: bu ülkeler arasında sınır kontrolü genelde yok. */
        val SCHENGEN = setOf(
            "AT", "BE", "BG", "HR", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IS", "IT", "LV", "LI", "LT", "LU",
            "MT", "NL", "NO", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "CH",
        )
    }
}
