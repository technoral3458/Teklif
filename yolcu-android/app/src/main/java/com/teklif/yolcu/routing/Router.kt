package com.teklif.yolcu.routing

import com.teklif.yolcu.geo.Geo
import com.teklif.yolcu.geo.LatLon
import com.teklif.yolcu.map.TileCache
import com.teklif.yolcu.nav.Maneuver
import com.teklif.yolcu.nav.Place
import com.teklif.yolcu.nav.Route
import com.teklif.yolcu.nav.TurnType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class RoutingException(message: String) : Exception(message)

/**
 * Rota hesaplar. Önce Valhalla (Türkçe anlatım üretir), uzun rotalarda reddederse OSRM
 * (anlatımı biz Türkçeleştiririz). İkisi de OpenStreetMap verisiyle çalışan ücretsiz sunucular.
 */
object Router {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    fun route(from: LatLon, to: Place): Route {
        val valhallaError = try {
            return valhalla(from, to)
        } catch (e: RoutingException) {
            e
        } catch (e: IOException) {
            e
        }
        try {
            return osrm(from, to)
        } catch (e: IOException) {
            throw RoutingException("Rota sunucusuna ulaşılamadı. İnternet bağlantısını kontrol edin.")
        } catch (e: RoutingException) {
            throw RoutingException("Rota bulunamadı (${valhallaError.message ?: "Valhalla"}; ${e.message}).")
        }
    }

    // ---------------- Valhalla ----------------

    private fun valhalla(from: LatLon, to: Place): Route {
        val body = JSONObject()
            .put("locations", JSONArray().put(loc(from)).put(loc(to.position)))
            .put("costing", "auto")
            .put("directions_options", JSONObject().put("units", "kilometers").put("language", "tr-TR"))
        val request = Request.Builder()
            .url("https://valhalla1.openstreetmap.de/route")
            .header("User-Agent", TileCache.USER_AGENT)
            .post(body.toString().toRequestBody(json))
            .build()
        val text = http.newCall(request).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { JSONObject(t).optString("error") }.getOrNull().orEmpty()
                throw RoutingException(msg.ifBlank { "Valhalla ${r.code}" })
            }
            t
        }
        val trip = JSONObject(text).getJSONObject("trip")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val shape = Geo.decodePolyline(leg.getString("shape"), 1e6)
        val ms = leg.getJSONArray("maneuvers")
        val maneuvers = ArrayList<Maneuver>(ms.length())
        for (i in 0 until ms.length()) {
            val m = ms.getJSONObject(i)
            val instruction = m.optString("instruction")
            val names = m.optJSONArray("street_names")
            maneuvers.add(
                Maneuver(
                    type = valhallaType(m.optInt("type")),
                    instruction = instruction,
                    verbalAlert = m.optString("verbal_transition_alert_instruction").ifBlank { instruction },
                    verbalPre = m.optString("verbal_pre_transition_instruction").ifBlank { instruction },
                    streetName = if (names != null && names.length() > 0) names.optString(0) else "",
                    beginShapeIndex = m.optInt("begin_shape_index"),
                    lengthM = m.optDouble("length", 0.0) * 1000,
                    timeS = m.optDouble("time", 0.0),
                    roundaboutExit = m.optInt("roundabout_exit_count", 0),
                )
            )
        }
        val summary = trip.getJSONObject("summary")
        return build(shape, maneuvers, summary.optDouble("length") * 1000, summary.optDouble("time"), to, "valhalla")
    }

    private fun loc(p: LatLon) = JSONObject().put("lat", p.lat).put("lon", p.lon)

    private fun valhallaType(t: Int): TurnType = when (t) {
        1, 2, 3 -> TurnType.DEPART
        4, 5, 6 -> TurnType.ARRIVE
        7, 8, 17, 22 -> TurnType.STRAIGHT
        9 -> TurnType.SLIGHT_RIGHT
        10 -> TurnType.RIGHT
        11 -> TurnType.SHARP_RIGHT
        12 -> TurnType.UTURN_RIGHT
        13 -> TurnType.UTURN_LEFT
        14 -> TurnType.SHARP_LEFT
        15 -> TurnType.LEFT
        16 -> TurnType.SLIGHT_LEFT
        18 -> TurnType.RAMP_RIGHT
        19 -> TurnType.RAMP_LEFT
        20 -> TurnType.EXIT_RIGHT
        21 -> TurnType.EXIT_LEFT
        23 -> TurnType.KEEP_RIGHT
        24 -> TurnType.KEEP_LEFT
        25, 37, 38 -> TurnType.MERGE
        26 -> TurnType.ROUNDABOUT
        27 -> TurnType.ROUNDABOUT_EXIT
        28, 29 -> TurnType.FERRY
        else -> TurnType.UNKNOWN
    }

    // ---------------- OSRM ----------------

    private fun osrm(from: LatLon, to: Place): Route {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.lon},${from.lat};${to.position.lon},${to.position.lat}" +
            "?overview=full&steps=true&geometries=polyline6"
        val request = Request.Builder().url(url).header("User-Agent", TileCache.USER_AGENT).build()
        val text = http.newCall(request).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RoutingException("OSRM ${r.code}")
            t
        }
        val root = JSONObject(text)
        if (root.optString("code") != "Ok") throw RoutingException(root.optString("message", "OSRM rota bulamadı"))
        val r = root.getJSONArray("routes").getJSONObject(0)
        val shape = Geo.decodePolyline(r.getString("geometry"), 1e6)
        val steps = r.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
        val maneuvers = ArrayList<Maneuver>(steps.length())
        // OSRM adımların şekil indeksini vermez: manevra konumunu rota şekliyle eşleştiririz.
        var searchFrom = 0
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            val man = s.getJSONObject("maneuver")
            val locArr = man.getJSONArray("location")
            val mp = LatLon(locArr.getDouble(1), locArr.getDouble(0))
            var best = searchFrom
            var bestD = Double.MAX_VALUE
            for (k in searchFrom until shape.size) {
                val d = Geo.distance(mp, shape[k])
                if (d < bestD) { bestD = d; best = k }
                if (d < 3) break
            }
            searchFrom = best
            val type = osrmType(man.optString("type"), man.optString("modifier"))
            val exit = man.optInt("exit", 0)
            val name = s.optString("name").ifBlank { s.optString("ref") }
            val ref = s.optString("ref")
            val dest = s.optString("destinations")
            val text = turkishInstruction(type, name, ref, dest, exit)
            maneuvers.add(
                Maneuver(
                    type = type,
                    instruction = text,
                    verbalAlert = text,
                    verbalPre = text,
                    streetName = name,
                    beginShapeIndex = best,
                    lengthM = s.optDouble("distance", 0.0),
                    timeS = s.optDouble("duration", 0.0),
                    roundaboutExit = exit,
                )
            )
        }
        return build(shape, maneuvers, r.optDouble("distance"), r.optDouble("duration"), to, "osrm")
    }

    private fun osrmType(type: String, modifier: String): TurnType {
        fun byModifier(): TurnType = when (modifier) {
            "left" -> TurnType.LEFT
            "right" -> TurnType.RIGHT
            "slight left" -> TurnType.SLIGHT_LEFT
            "slight right" -> TurnType.SLIGHT_RIGHT
            "sharp left" -> TurnType.SHARP_LEFT
            "sharp right" -> TurnType.SHARP_RIGHT
            "uturn" -> TurnType.UTURN_LEFT
            else -> TurnType.STRAIGHT
        }
        return when (type) {
            "depart" -> TurnType.DEPART
            "arrive" -> TurnType.ARRIVE
            "roundabout", "rotary", "roundabout turn" -> TurnType.ROUNDABOUT
            "exit roundabout", "exit rotary" -> TurnType.ROUNDABOUT_EXIT
            "merge" -> TurnType.MERGE
            "on ramp" -> if (modifier.contains("left")) TurnType.RAMP_LEFT else TurnType.RAMP_RIGHT
            "off ramp" -> if (modifier.contains("left")) TurnType.EXIT_LEFT else TurnType.EXIT_RIGHT
            "fork" -> if (modifier.contains("left")) TurnType.KEEP_LEFT else TurnType.KEEP_RIGHT
            "new name", "continue", "notification" -> if (modifier == "straight" || modifier.isBlank()) TurnType.STRAIGHT else byModifier()
            "end of road", "turn" -> byModifier()
            else -> byModifier()
        }
    }

    /** OSRM için Türkçe talimat üretir. */
    private fun turkishInstruction(type: TurnType, name: String, ref: String, dest: String, exit: Int): String {
        val road = when {
            name.isNotBlank() && ref.isNotBlank() && name != ref -> "$name ($ref)"
            name.isNotBlank() -> name
            ref.isNotBlank() -> ref
            else -> ""
        }
        val toward = dest.takeIf { it.isNotBlank() }?.let { " – ${it.replace(",", ", ")} yönü" } ?: ""
        val onto = if (road.isNotBlank()) ", $road$toward" else toward
        return when (type) {
            TurnType.DEPART -> if (road.isNotBlank()) "$road üzerinde yola çıkın" else "Yola çıkın"
            TurnType.ARRIVE -> "Hedefinize vardınız"
            TurnType.STRAIGHT -> "Düz devam edin$onto"
            TurnType.SLIGHT_LEFT -> "Hafif sola dönün$onto"
            TurnType.SLIGHT_RIGHT -> "Hafif sağa dönün$onto"
            TurnType.LEFT -> "Sola dönün$onto"
            TurnType.RIGHT -> "Sağa dönün$onto"
            TurnType.SHARP_LEFT -> "Keskin sola dönün$onto"
            TurnType.SHARP_RIGHT -> "Keskin sağa dönün$onto"
            TurnType.UTURN_LEFT, TurnType.UTURN_RIGHT -> "U dönüşü yapın$onto"
            TurnType.RAMP_LEFT -> "Soldaki bağlantı yolundan girin$onto"
            TurnType.RAMP_RIGHT -> "Sağdaki bağlantı yolundan girin$onto"
            TurnType.EXIT_LEFT -> "Soldan çıkın$onto"
            TurnType.EXIT_RIGHT -> "Sağdan çıkın$onto"
            TurnType.KEEP_LEFT -> "Solda kalın$onto"
            TurnType.KEEP_RIGHT -> "Sağda kalın$onto"
            TurnType.MERGE -> "Trafiğe katılın$onto"
            TurnType.ROUNDABOUT -> if (exit > 0) "Döner kavşakta $exit. çıkıştan çıkın$onto" else "Döner kavşağa girin$onto"
            TurnType.ROUNDABOUT_EXIT -> "Döner kavşaktan çıkın$onto"
            TurnType.FERRY -> "Feribota binin"
            TurnType.UNKNOWN -> "Devam edin$onto"
        }
    }

    // ---------------- Ortak ----------------

    private fun build(shape: List<LatLon>, maneuvers: List<Maneuver>, lengthM: Double, timeS: Double, to: Place, provider: String): Route {
        if (shape.size < 2) throw RoutingException("Rota şekli boş")
        val cumulative = DoubleArray(shape.size)
        for (i in 1 until shape.size) {
            cumulative[i] = cumulative[i - 1] + Geo.distance(shape[i - 1], shape[i])
        }
        val total = if (lengthM > 0) lengthM else cumulative.last()
        return Route(shape, cumulative, maneuvers, total, timeS, to, provider = provider)
    }
}
