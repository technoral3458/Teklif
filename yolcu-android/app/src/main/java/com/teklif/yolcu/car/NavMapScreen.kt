package com.teklif.yolcu.car

import android.graphics.Rect
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.teklif.yolcu.R
import com.teklif.yolcu.geo.Geo
import com.teklif.yolcu.geo.LatLon
import com.teklif.yolcu.map.Camera
import com.teklif.yolcu.map.MapOverlay
import com.teklif.yolcu.map.MapRenderer
import com.teklif.yolcu.map.TileCache
import com.teklif.yolcu.nav.Nav
import com.teklif.yolcu.nav.NavSnapshot
import com.teklif.yolcu.nav.TurnType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/** Araç ekranı: harita yüzeye çizilir, dönüş bilgisi ve sınır şablonla gösterilir. */
@OptIn(FlowPreview::class)
class NavMapScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private val tiles = TileCache.get(carContext)
    private val renderer = MapRenderer(tiles)
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var visibleArea: Rect? = null
    private var follow = true
    private var zoom = 14.0
    private var center = LatLon(41.0, 29.0)
    private var lastNavigating = false
    private val tileListener: () -> Unit = { render() }

    init {
        lifecycle.addObserver(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        carContext.getCarService(NavigationManager::class.java).setNavigationManagerCallback(
            object : NavigationManagerCallback {
                override fun onStopNavigation() {
                    Nav.stopNavigation()
                }
            }
        )
        tiles.addListener(tileListener)
        lifecycleScope.launch {
            Nav.state.sample(400).collect { s ->
                syncNavigationManager(s)
                render()
                invalidate()
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        tiles.removeListener(tileListener)
    }

    private fun syncNavigationManager(s: NavSnapshot) {
        if (s.navigating == lastNavigating) return
        lastNavigating = s.navigating
        val nm = carContext.getCarService(NavigationManager::class.java)
        runCatching { if (s.navigating) nm.navigationStarted() else nm.navigationEnded() }
    }

    // ---- Yüzey ----

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container.surface
        width = container.width
        height = container.height
        render()
    }

    override fun onVisibleAreaChanged(area: Rect) {
        visibleArea = area
        render()
    }

    override fun onStableAreaChanged(area: Rect) {}

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        surface = null
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        follow = false
        val density = carContext.resources.displayMetrics.density
        val mpp = Geo.metersPerPixel(center.lat, zoom) / density * 2.0
        val s = Nav.state.value
        val bearing = if (s.navigating) (s.heading ?: 0f).toDouble() else 0.0
        val b = Math.toRadians(bearing)
        val dx = distanceX * cos(b) - distanceY * sin(b)
        val dy = distanceX * sin(b) + distanceY * cos(b)
        center = LatLon(
            (center.lat - dy * mpp / 111_320.0).coerceIn(-85.0, 85.0),
            center.lon + dx * mpp / (111_320.0 * cos(Math.toRadians(center.lat))),
        )
        render()
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        zoom = (zoom + ln(scaleFactor.toDouble()) / ln(2.0)).coerceIn(3.0, 19.0)
        render()
    }

    override fun onFling(velocityX: Float, velocityY: Float) {}

    private fun render() {
        val surf = surface ?: return
        val s = Nav.state.value
        if (follow) s.location?.let { center = it }
        val bearing = if (s.navigating && follow) (s.heading ?: 0f).toDouble() else 0.0
        val camera = Camera(center, zoom, bearing)
        val overlay = MapOverlay(
            route = s.route?.shape ?: emptyList(),
            travelledUntilIndex = s.progress?.shapeIndex ?: 0,
            position = s.location,
            heading = s.heading,
            destination = s.route?.destination?.position,
            borderPoints = s.route?.borders?.map { it.point to (it.crossingName ?: "${it.toName} sınırı") } ?: emptyList(),
        )
        val canvas = try { surf.lockCanvas(null) } catch (e: Exception) { null } ?: return
        try {
            renderer.draw(canvas, width, height, camera, overlay, carContext.resources.displayMetrics.density)
        } finally {
            runCatching { surf.unlockCanvasAndPost(canvas) }
        }
    }

    // ---- Şablon ----

    override fun onGetTemplate(): Template {
        val s = Nav.state.value
        val builder = NavigationTemplate.Builder().setBackgroundColor(CarColor.DEFAULT)

        val actions = ActionStrip.Builder()
        actions.addAction(
            Action.Builder().setTitle("Ara").setOnClickListener { screenManager.push(CarSearchScreen(carContext)) }.build()
        )
        when {
            s.navigating -> actions.addAction(Action.Builder().setTitle("Durdur").setOnClickListener { Nav.stopNavigation() }.build())
            s.route != null -> actions.addAction(Action.Builder().setTitle("Başlat").setOnClickListener { Nav.startNavigation() }.build())
        }
        builder.setActionStrip(actions.build())

        builder.setMapActionStrip(
            ActionStrip.Builder()
                .addAction(iconAction(R.drawable.ic_my_location) { follow = true; zoom = if (Nav.state.value.navigating) 15.5 else 14.0; render() })
                .addAction(iconAction(R.drawable.ic_zoom_in) { zoom = (zoom + 1).coerceAtMost(19.0); render() })
                .addAction(iconAction(R.drawable.ic_zoom_out) { zoom = (zoom - 1).coerceAtLeast(3.0); render() })
                .build()
        )

        val p = s.progress
        if (s.navigating) {
            val m = p?.nextManeuver
            if (s.rerouting || m == null) {
                builder.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
            } else {
                val cue = buildString {
                    append(m.instruction)
                    p.nextBorder?.let { b ->
                        append("  •  ${b.toName} sınırı ${Geo.formatDistance(p.distanceToBorderM)}")
                        b.crossingName?.let { append(" ($it)") }
                    }
                }
                val step = Step.Builder(cue).setManeuver(carManeuver(m.type, m.roundaboutExit)).build()
                builder.setNavigationInfo(
                    RoutingInfo.Builder().setCurrentStep(step, carDistance(p.distanceToManeuverM)).build()
                )
            }
            if (p != null) {
                val arrival = System.currentTimeMillis() + (p.remainingS * 1000).toLong()
                builder.setDestinationTravelEstimate(
                    TravelEstimate.Builder(carDistance(p.remainingM), DateTimeWithZone.create(arrival, TimeZone.getDefault()))
                        .setRemainingTimeSeconds(p.remainingS.toLong())
                        .build()
                )
            }
        }
        return builder.build()
    }

    private fun iconAction(res: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener(onClick)
            .build()

    companion object {
        fun carDistance(m: Double): Distance = when {
            m < 1000 -> Distance.create(((m / 10).toInt() * 10).toDouble().coerceAtLeast(0.0), Distance.UNIT_METERS)
            m < 10_000 -> Distance.create(m / 1000, Distance.UNIT_KILOMETERS_P1)
            else -> Distance.create((m / 1000).toInt().toDouble(), Distance.UNIT_KILOMETERS)
        }

        fun carManeuver(t: TurnType, exit: Int): Maneuver {
            val type = when (t) {
                TurnType.DEPART -> Maneuver.TYPE_DEPART
                TurnType.ARRIVE -> Maneuver.TYPE_DESTINATION
                TurnType.STRAIGHT, TurnType.UNKNOWN -> Maneuver.TYPE_STRAIGHT
                TurnType.SLIGHT_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT
                TurnType.SLIGHT_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
                TurnType.LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT
                TurnType.RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT
                TurnType.SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
                TurnType.SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
                TurnType.UTURN_LEFT -> Maneuver.TYPE_U_TURN_LEFT
                TurnType.UTURN_RIGHT -> Maneuver.TYPE_U_TURN_RIGHT
                TurnType.RAMP_LEFT -> Maneuver.TYPE_ON_RAMP_NORMAL_LEFT
                TurnType.RAMP_RIGHT -> Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT
                TurnType.EXIT_LEFT -> Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT
                TurnType.EXIT_RIGHT -> Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT
                TurnType.KEEP_LEFT -> Maneuver.TYPE_KEEP_LEFT
                TurnType.KEEP_RIGHT -> Maneuver.TYPE_KEEP_RIGHT
                TurnType.MERGE -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED
                TurnType.ROUNDABOUT -> if (exit > 0) Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW else Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
                TurnType.ROUNDABOUT_EXIT -> Maneuver.TYPE_ROUNDABOUT_EXIT_CCW
                TurnType.FERRY -> Maneuver.TYPE_FERRY_BOAT
            }
            val b = Maneuver.Builder(type)
            if (type == Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW) b.setRoundaboutExitNumber(exit)
            return b.build()
        }
    }
}
