package com.teklif.yolcu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.teklif.yolcu.geo.Geo
import com.teklif.yolcu.geo.LatLon
import com.teklif.yolcu.map.Camera
import com.teklif.yolcu.map.MapOverlay
import com.teklif.yolcu.map.MapRenderer
import com.teklif.yolcu.map.TileCache
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/** Kaydırılıp yakınlaştırılabilen harita. Kullanıcı dokununca [onUserMoved] çağrılır (takip kapanır). */
@Composable
fun MapView(
    camera: Camera,
    overlay: MapOverlay,
    onCameraChange: (Camera) -> Unit,
    onUserMoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val tiles = remember { TileCache.get(context) }
    val renderer = remember { MapRenderer(tiles) }
    var tick by remember { mutableIntStateOf(0) }
    val cam by rememberUpdatedState(camera)
    val onChange by rememberUpdatedState(onCameraChange)
    val onMoved by rememberUpdatedState(onUserMoved)

    DisposableEffect(tiles) {
        val l: () -> Unit = { tick++ }
        tiles.addListener(l)
        onDispose { tiles.removeListener(l) }
    }

    Canvas(
        modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                onMoved()
                val mpp = Geo.metersPerPixel(cam.center.lat, cam.zoom) / density * 2.0
                // Harita döndürülmüşse kaydırma vektörünü geri döndür.
                val b = Math.toRadians(cam.bearing)
                val dx = pan.x * cos(b) - pan.y * sin(b)
                val dy = pan.x * sin(b) + pan.y * cos(b)
                val dLat = dy * mpp / 111_320.0
                val dLon = -dx * mpp / (111_320.0 * cos(Math.toRadians(cam.center.lat)))
                val newZoom = (cam.zoom + ln(zoom.toDouble()) / ln(2.0)).coerceIn(3.0, 19.0)
                onChange(
                    cam.copy(
                        center = LatLon((cam.center.lat + dLat).coerceIn(-85.0, 85.0), cam.center.lon + dLon),
                        zoom = newZoom,
                    )
                )
            }
        },
    ) {
        tick // karo gelince yeniden çiz
        drawIntoCanvas { c ->
            renderer.draw(c.nativeCanvas, size.width.toInt(), size.height.toInt(), camera, overlay, density)
        }
    }
}
