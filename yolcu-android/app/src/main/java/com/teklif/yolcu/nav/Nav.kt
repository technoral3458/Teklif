package com.teklif.yolcu.nav

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import com.teklif.yolcu.geo.Countries
import com.teklif.yolcu.geo.LatLon
import com.teklif.yolcu.routing.Geocoder
import com.teklif.yolcu.routing.Router
import com.teklif.yolcu.routing.RoutingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Uygulamanın tek navigasyon durumu. Telefon ekranı, Android Auto ekranı ve konum servisi
 * aynı nesneyi kullanır.
 */
@SuppressLint("StaticFieldLeak")
object Nav {

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(NavSnapshot())
    val state: StateFlow<NavSnapshot> = _state.asStateFlow()

    private lateinit var countries: Countries
    private lateinit var trip: TripRecorder
    private val engine = NavigationEngine { text -> speaker?.invoke(text) }

    /** Servis tarafından takılan sesli okuma. */
    @Volatile var speaker: ((String) -> Unit)? = null

    private var routingJob: Job? = null
    private var appVisible = false
    private var carVisible = false
    private var lastCountryIso: String? = null

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        trip = TripRecorder(app)
        _state.update { it.copy(trip = trip.stats) }
        scope.launch(Dispatchers.IO) {
            countries = Countries.get(app)
        }
        engine.onOffRoute = { reroute() }
        engine.onArrived = { scope.launch { stopNavigation(keepRoute = false) } }
    }

    // ---- Görünürlük: servis yalnızca gerektiğinde çalışsın ----

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        syncService()
    }

    fun setCarVisible(visible: Boolean) {
        carVisible = visible
        syncService()
    }

    private fun syncService() {
        val needed = appVisible || carVisible || _state.value.navigating
        val intent = Intent(app, LocationService::class.java)
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            app, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needed && granted) {
            intent.action = LocationService.ACTION_START
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
        } else {
            app.stopService(intent)
        }
    }

    // ---- Konum ----

    fun onLocation(loc: Location) {
        val p = LatLon(loc.latitude, loc.longitude)
        val speed = if (loc.hasSpeed()) loc.speed else 0f
        val heading = if (loc.hasBearing() && speed > 1f) loc.bearing else _state.value.heading
        val country = if (::countries.isInitialized) countries.at(p.lat, p.lon) else null
        val iso = country?.iso2

        // Ülke değişti: karşılama ve ülke içi sayaç.
        if (country != null && lastCountryIso != null && country.iso2 != lastCountryIso) {
            trip.onBorderCrossed()
            speaker?.invoke("${country.nameTr}'${suffixDative(country.nameTr)} hoş geldiniz.")
        }
        if (iso != null) lastCountryIso = iso

        trip.onLocation(p, loc.accuracy, speed, iso)
        val progress = if (_state.value.navigating) {
            engine.update(p, speed, loc.accuracy, System.currentTimeMillis())
        } else {
            null
        }
        _state.update {
            it.copy(
                location = p,
                speedKmh = speed * 3.6,
                heading = heading,
                accuracyM = loc.accuracy,
                country = country?.let { c -> CountryInfo(c.iso2, c.nameTr, c.flag) },
                progress = progress ?: if (it.navigating) it.progress else null,
                trip = trip.stats,
                gpsReady = true,
            )
        }
    }

    /** "Bulgaristan'a", "Yunanistan'a", "Almanya'ya", "Sırbistan'a", "Çekya'ya" için yönelme eki. */
    private fun suffixDative(name: String): String {
        val last = name.lastOrNull()?.lowercaseChar() ?: return "a"
        val vowels = "aeıioöuü"
        val lastVowel = name.lowercase().lastOrNull { it in vowels } ?: 'a'
        val back = lastVowel in "aıou"
        val ending = if (back) "a" else "e"
        return if (last in vowels) "y$ending" else ending
    }

    // ---- Rota ----

    /** @param autoStart rota gelince navigasyonu hemen başlat (Android Auto). */
    fun planRoute(place: Place, autoStart: Boolean = false) {
        val from = _state.value.location ?: run {
            message("Konum henüz alınamadı. GPS'in açık olduğundan emin olun.")
            return
        }
        routingJob?.cancel()
        _state.update { it.copy(routing = true, message = null) }
        routingJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val r = Router.route(from, place)
                    r.copy(borders = RouteBorders.compute(r, Countries.get(app)))
                }
            }
            result.onSuccess { r ->
                _state.update { it.copy(route = r, routing = false, progress = null) }
                if (_state.value.navigating || autoStart) startNavigation()
            }.onFailure { e ->
                _state.update { it.copy(routing = false) }
                message((e as? RoutingException)?.message ?: "Rota hesaplanamadı: ${e.message}")
            }
        }
    }

    fun startNavigation() {
        val r = _state.value.route ?: return
        engine.setRoute(r)
        _state.update { it.copy(navigating = true, message = null) }
        syncService()
        speaker?.invoke("Navigasyon başladı. ${r.destination.name} hedefine ${com.teklif.yolcu.geo.Geo.formatDistance(r.lengthM)}.")
    }

    fun stopNavigation(keepRoute: Boolean = false) {
        engine.setRoute(null)
        _state.update { it.copy(navigating = false, progress = null, route = if (keepRoute) it.route else null, rerouting = false) }
        syncService()
    }

    fun clearRoute() {
        routingJob?.cancel()
        _state.update { it.copy(route = null, routing = false, progress = null) }
    }

    private fun reroute() {
        val s = _state.value
        val dest = s.route?.destination ?: return
        val from = s.location ?: return
        if (s.rerouting) return
        _state.update { it.copy(rerouting = true) }
        speaker?.invoke("Rota yeniden hesaplanıyor.")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val r = Router.route(from, dest)
                    r.copy(borders = RouteBorders.compute(r, Countries.get(app)))
                }
            }
            result.onSuccess { r ->
                engine.setRoute(r)
                _state.update { it.copy(route = r, rerouting = false) }
            }.onFailure {
                _state.update { it.copy(rerouting = false) }
            }
        }
    }

    // ---- Diğer ----

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        runCatching { Geocoder.search(query, _state.value.location) }.getOrDefault(emptyList())
    }

    fun resetTrip() {
        trip.reset()
        _state.update { it.copy(trip = trip.stats) }
    }

    fun saveTrip() {
        if (::trip.isInitialized) trip.save()
    }

    fun message(text: String?) = _state.update { it.copy(message = text) }
}
