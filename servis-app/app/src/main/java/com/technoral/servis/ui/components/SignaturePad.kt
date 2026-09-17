package com.technoral.servis.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Path as ComposePath

/**
 * Parmakla imza alma alanı. Çizim, ekrandan bağımsız olarak beyaz zeminli
 * bir PNG'ye aktarılır; PDF ve mail eklerinde bu görsel kullanılır.
 */
@Composable
fun SignaturePad(
    onSave: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Snapshot listesi: iç listeyi değiştirmek yeniden çizim tetiklemediği için
    // her nokta eklenişinde son çizgi yeni bir liste olarak yazılıyor.
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val canvasSize = remember { mutableStateListOf(IntSize.Zero) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Müşteri yetkilisi aşağıdaki alana imza atabilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    canvasSize[0] = IntSize(size.width, size.height)
                    detectDragGestures(
                        onDragStart = { offset -> strokes.add(listOf(offset)) },
                        onDrag = { change, _ ->
                            change.consume()
                            if (strokes.isNotEmpty()) {
                                strokes[strokes.lastIndex] = strokes.last() + change.position
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                canvasSize[0] = IntSize(size.width.toInt(), size.height.toInt())
                strokes.forEach { points ->
                    if (points.size < 2) return@forEach
                    val path = ComposePath()
                    path.moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { path.lineTo(it.x, it.y) }
                    drawPath(
                        path = path,
                        color = Color(0xFF101820),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            if (strokes.isEmpty()) {
                Text(
                    "İmza alanı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AA7B2),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
                Icon(Icons.Default.Undo, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Geri al")
            }
            TextButton(onClick = { strokes.clear() }) { Text("Temizle") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Vazgeç") }
            Button(
                onClick = {
                    val size = canvasSize.firstOrNull() ?: IntSize.Zero
                    val bitmap = renderSignature(strokes, size.width, size.height)
                    if (bitmap != null) onSave(bitmap) else onCancel()
                },
                enabled = strokes.isNotEmpty(),
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Kaydet")
            }
        }
    }
}

private fun renderSignature(strokes: List<List<Offset>>, width: Int, height: Int): Bitmap? {
    if (strokes.isEmpty() || width <= 0 || height <= 0) return null
    val scale = 2
    val bitmap = Bitmap.createBitmap(width * scale, height * scale, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#101820")
        style = AndroidPaint.Style.STROKE
        strokeWidth = 3.5f * scale
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.forEach { points ->
        if (points.size < 2) return@forEach
        val path = AndroidPath()
        path.moveTo(points.first().x * scale, points.first().y * scale)
        points.drop(1).forEach { path.lineTo(it.x * scale, it.y * scale) }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
