package com.teklif.yolcu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.teklif.yolcu.geo.Geo
import com.teklif.yolcu.geo.LatLon
import com.teklif.yolcu.map.Camera
import com.teklif.yolcu.map.MapOverlay
import com.teklif.yolcu.nav.Nav
import com.teklif.yolcu.nav.NavSnapshot
import com.teklif.yolcu.nav.Place
import com.teklif.yolcu.nav.TurnType
import com.teklif.yolcu.nav.displayHeading
import com.teklif.yolcu.nav.displayPosition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(state: NavSnapshot, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    var camera by remember { mutableStateOf(Camera(LatLon(41.0, 29.0), 6.0)) }
    var follow by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Takip modu: konumu ortala; navigasyonda gidiş yönü yukarı ve yakın.
    LaunchedEffect(state.location, state.heading, state.navigating, follow) {
        val loc = state.location ?: return@LaunchedEffect
        if (!follow) return@LaunchedEffect
        camera = if (state.navigating) {
            Camera(state.displayPosition ?: loc, 16.3, (state.displayHeading ?: 0f).toDouble())
        } else {
            Camera(loc, if (camera.zoom < 12) 14.0 else camera.zoom, 0.0)
        }
    }
    // Rota gelince tüm rotayı göster; navigasyon başlayınca takibe dön.
    LaunchedEffect(state.route?.shape?.size, state.navigating) {
        val r = state.route ?: return@LaunchedEffect
        if (state.navigating) {
            follow = true
            return@LaunchedEffect
        }
        follow = false
        camera = fitCamera(r.shape)
    }

    val overlay = MapOverlay(
        route = state.route?.shape ?: emptyList(),
        travelledUntilIndex = state.progress?.shapeIndex ?: 0,
        position = state.displayPosition,
        heading = state.displayHeading,
        destination = state.route?.destination?.position,
        borderPoints = state.route?.borders?.map { it.point to (it.crossingName ?: "${it.toName} sınırı") } ?: emptyList(),
    )

    Box(Modifier.fillMaxSize()) {
        MapView(
            camera = camera,
            overlay = overlay,
            onCameraChange = { camera = it },
            onUserMoved = { follow = false },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Arama
            if (!state.navigating) {
                Card(Modifier.padding(12.dp).fillMaxWidth(), elevation = CardDefaults.cardElevation(6.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Nereye?") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = emptyList() }) {
                                Icon(Icons.Default.Close, "Temizle")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            scope.launch { searching = true; results = Nav.search(query); searching = false }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (searching) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    if (results.isNotEmpty()) {
                        LazyColumn(Modifier.heightIn(max = 260.dp)) {
                            items(results) { p ->
                                Column(
                                    Modifier.fillMaxWidth().clickable {
                                        results = emptyList(); query = p.name
                                        Nav.planRoute(p)
                                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    if (p.detail.isNotBlank()) Text(p.detail, style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                // Arama yazıldıkça (yarım saniye bekleyerek) öner.
                LaunchedEffect(query) {
                    if (query.length < 3) { results = emptyList(); return@LaunchedEffect }
                    delay(600)
                    searching = true
                    results = Nav.search(query)
                    searching = false
                }
            } else {
                ManeuverCard(state)
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                FloatingActionButton(onClick = { follow = true }, containerColor = if (follow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface) {
                    Icon(Icons.Default.MyLocation, "Konumuma git")
                }
            }
            Spacer(Modifier.height(8.dp))

            state.message?.let { msg ->
                Snackbar(Modifier.padding(horizontal = 12.dp), action = { TextButton(onClick = { Nav.message(null) }) { Text("Tamam") } }) { Text(msg) }
                Spacer(Modifier.height(8.dp))
            }

            if (!hasPermission) {
                Card(Modifier.padding(12.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Konum izni gerekli", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRequestPermission) { Text("İzin ver") }
                    }
                }
            } else {
                BottomPanel(state)
            }
        }
    }
}

/** Rotanın tamamını ekrana sığdıran kamera. */
private fun fitCamera(shape: List<LatLon>): Camera {
    val minLat = shape.minOf { it.lat }; val maxLat = shape.maxOf { it.lat }
    val minLon = shape.minOf { it.lon }; val maxLon = shape.maxOf { it.lon }
    val span = maxOf(maxLat - minLat, (maxLon - minLon) * Math.cos(Math.toRadians((minLat + maxLat) / 2)))
    val zoom = (Math.log(360.0 / (span * 1.6)) / Math.log(2.0)).coerceIn(4.0, 15.0)
    return Camera(LatLon((minLat + maxLat) / 2, (minLon + maxLon) / 2), zoom, 0.0)
}

@Composable
private fun ManeuverCard(state: NavSnapshot) {
    val p = state.progress
    val m = p?.nextManeuver
    Card(
        Modifier.padding(12.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(turnIcon(m?.type ?: TurnType.STRAIGHT), null, tint = Color.White, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (p != null && m != null) Geo.formatDistance(p.distanceToManeuverM) else "…",
                    color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp,
                )
                Text(
                    text = when {
                        state.rerouting -> "Rota yeniden hesaplanıyor…"
                        p?.offRoute == true -> "Rotadan çıktınız"
                        m != null -> m.instruction
                        else -> "GPS bekleniyor…"
                    },
                    color = Color.White, fontSize = 18.sp, lineHeight = 22.sp, maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun BottomPanel(state: NavSnapshot) {
    Card(
        Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            when {
                state.routing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Rota hesaplanıyor…")
                }
                state.navigating -> NavigatingPanel(state)
                state.route != null -> RoutePreviewPanel(state)
                else -> IdlePanel(state)
            }
        }
    }
}

@Composable
private fun RoutePreviewPanel(state: NavSnapshot) {
    val r = state.route ?: return
    Text(r.destination.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
    Text("${Geo.formatDistance(r.lengthM)} · ${Geo.formatDuration(r.timeS)}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    if (r.borders.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        r.borders.forEach { b ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(flagOf(b.toIso), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(b.crossingName ?: b.toName, Modifier.weight(1f), maxLines = 1)
                Text("${(b.distanceAlongM / 1000).toInt()} km", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { Nav.startNavigation() }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Navigation, null); Spacer(Modifier.width(6.dp)); Text("Başlat")
        }
        OutlinedButton(onClick = { Nav.clearRoute() }) { Text("Vazgeç") }
    }
}

@Composable
private fun NavigatingPanel(state: NavSnapshot) {
    val p = state.progress
    val eta = p?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (it.remainingS * 1000).toLong())) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (p != null) "${Geo.formatDistance(p.remainingM)} · ${Geo.formatDuration(p.remainingS)} · $eta" else "…",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            )
            val b = p?.nextBorder
            if (b != null) {
                Text(
                    "${flagOf(b.toIso)} ${b.crossingName ?: b.toName} · ${Geo.formatDistance(p.distanceToBorderM)}",
                    fontSize = 16.sp, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
            }
            TripLine(state)
        }
        Button(onClick = { Nav.stopNavigation() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Text("Durdur")
        }
    }
}

@Composable
private fun IdlePanel(state: NavSnapshot) {
    val c = state.country
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(c?.flag ?: "🌍", fontSize = 30.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c?.name ?: if (state.gpsReady) "—" else "GPS bekleniyor…", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TripLine(state)
        }
        TextButton(onClick = { Nav.resetTrip() }) { Text("Sıfırla") }
    }
}

/** Tek satır: toplam km ve bulunulan ülkedeki km. */
@Composable
private fun TripLine(state: NavSnapshot) {
    val t = state.trip
    val c = state.country
    val inCountry = c?.let { t.byCountryM[it.iso] } ?: 0.0
    Text(
        if (c != null) "Toplam ${Geo.formatDistance(t.totalM)} · ${c.flag} ${Geo.formatDistance(inCountry)}"
        else "Toplam ${Geo.formatDistance(t.totalM)}",
        style = MaterialTheme.typography.bodyMedium,
    )
}

fun flagOf(iso: String): String =
    iso.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

fun turnIcon(t: TurnType): ImageVector = when (t) {
    TurnType.DEPART, TurnType.STRAIGHT, TurnType.UNKNOWN -> Icons.Default.Straight
    TurnType.ARRIVE -> Icons.Default.Flag
    TurnType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    TurnType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    TurnType.LEFT -> Icons.Default.TurnLeft
    TurnType.RIGHT -> Icons.Default.TurnRight
    TurnType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    TurnType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    TurnType.UTURN_LEFT -> Icons.Default.UTurnLeft
    TurnType.UTURN_RIGHT -> Icons.Default.UTurnRight
    TurnType.RAMP_LEFT -> Icons.Default.RampLeft
    TurnType.RAMP_RIGHT -> Icons.Default.RampRight
    TurnType.EXIT_LEFT, TurnType.KEEP_LEFT -> Icons.Default.ForkLeft
    TurnType.EXIT_RIGHT, TurnType.KEEP_RIGHT -> Icons.Default.ForkRight
    TurnType.MERGE -> Icons.Default.Merge
    TurnType.ROUNDABOUT, TurnType.ROUNDABOUT_EXIT -> Icons.Default.RoundaboutLeft
    TurnType.FERRY -> Icons.Default.DirectionsBoat
}
