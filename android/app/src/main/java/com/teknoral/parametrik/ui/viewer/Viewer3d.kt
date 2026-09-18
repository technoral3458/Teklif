package com.teknoral.parametrik.ui.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.teknoral.parametrik.domain.model.Geometry

private val ViewerStateSaver = Saver<ViewerState, FloatArray>(
    save = { floatArrayOf(it.yaw, it.pitch, it.zoom, it.panX, it.panY) },
    restore = { ViewerState(it[0], it[1], it[2], it[3], it[4]) }
)

/**
 * 3B görünüm + hazır kamera açısı düğmeleri.
 * Kamera durumu ekran döndürmede korunur.
 */
@Composable
fun Viewer3d(
    geometry: Geometry,
    modifier: Modifier = Modifier,
    emptyHint: String = "Bu iş henüz dilimlenmedi. Önce dilimleyin."
) {
    var state by rememberSaveable(stateSaver = ViewerStateSaver) { mutableStateOf(ViewerState()) }
    var selectedPreset by rememberSaveable { mutableStateOf(ViewPreset.PERSPECTIVE.name) }
    val viewRef = remember { mutableStateOf<Model3dView?>(null) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    Model3dView(context).also { view ->
                        view.setHint(emptyHint)
                        view.applyState(state)
                        view.setGeometry(geometry)
                        view.onStateChanged = { state = it }
                        viewRef.value = view
                    }
                },
                update = { view ->
                    view.setHint(emptyHint)
                    view.setGeometry(geometry)
                    if (view.currentState() != state) view.applyState(state)
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ViewPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset.name,
                    onClick = {
                        selectedPreset = preset.name
                        viewRef.value?.applyPreset(preset)
                        state = ViewerState(preset.yaw, preset.pitch, 1f, 0f, 0f)
                    },
                    label = { Text(preset.label) }
                )
            }
            Text(
                text = "Tek parmak: döndür · İki parmak: yakınlaştır/kaydır · Çift dokun: sıfırla",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
