package com.teknoral.parametrik.ui.capture

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teknoral.parametrik.data.media.CropRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Basit kırpma: dikdörtgenin içini sürükle → taşı, köşeye yakın sürükle → boyutlandır.
 * Sonuç normalleştirilmiş (0..1) olarak döner; ölçek küçültme yükleme sırasında yapılır.
 */
@Composable
fun CropScreen(
    imageUri: Uri,
    onConfirm: (Uri, CropRect) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var aspectRatio by remember { mutableStateOf(1f) }
    var crop by remember { mutableStateOf(CropRect.FULL) }

    LaunchedEffect(imageUri) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
        if (options.outWidth > 0 && options.outHeight > 0) {
            aspectRatio = options.outWidth.toFloat() / options.outHeight
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Çekilen fotoğraf",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(aspectRatio) {
                        var handle = HANDLE_NONE
                        detectDragGestures(
                            onDragStart = { offset ->
                                val area = imageArea(size.width.toFloat(), size.height.toFloat(), aspectRatio)
                                handle = handleAt(offset.x, offset.y, crop, area)
                            },
                            onDragEnd = { handle = HANDLE_NONE }
                        ) { change, dragAmount ->
                            change.consume()
                            val area = imageArea(size.width.toFloat(), size.height.toFloat(), aspectRatio)
                            if (area[2] <= 0f || area[3] <= 0f) return@detectDragGestures
                            val dx = dragAmount.x / area[2]
                            val dy = dragAmount.y / area[3]
                            crop = moveCrop(crop, handle, dx, dy)
                        }
                    }
            ) {
                val area = imageArea(size.width, size.height, aspectRatio)
                val left = area[0] + crop.left * area[2]
                val top = area[1] + crop.top * area[3]
                val width = (crop.right - crop.left) * area[2]
                val height = (crop.bottom - crop.top) * area[3]

                // Seçili alanın dışını karart (katman gerektirmeyen dört bant).
                val dim = Color.Black.copy(alpha = 0.45f)
                drawRect(dim, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, top))
                drawRect(
                    dim,
                    androidx.compose.ui.geometry.Offset(0f, top + height),
                    androidx.compose.ui.geometry.Size(size.width, (size.height - top - height).coerceAtLeast(0f))
                )
                drawRect(dim, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(left, height))
                drawRect(
                    dim,
                    androidx.compose.ui.geometry.Offset(left + width, top),
                    androidx.compose.ui.geometry.Size((size.width - left - width).coerceAtLeast(0f), height)
                )
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, height),
                    style = Stroke(width = 3f)
                )
                val handleSize = 28f
                listOf(
                    left to top,
                    left + width to top,
                    left to top + height,
                    left + width to top + height
                ).forEach { (hx, hy) ->
                    drawRect(
                        color = Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(hx - handleSize / 2, hy - handleSize / 2),
                        size = androidx.compose.ui.geometry.Size(handleSize, handleSize),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Kabartmaya dönüşecek alanı seçin.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Yeniden çek") }
                OutlinedButton(
                    onClick = { crop = CropRect.FULL },
                    modifier = Modifier.weight(1f)
                ) { Text("Tümü") }
                Button(
                    onClick = { onConfirm(imageUri, crop) },
                    modifier = Modifier.weight(1f)
                ) { Text("Devam") }
            }
        }
    }
}

private const val HANDLE_NONE = 0
private const val HANDLE_MOVE = 1
private const val HANDLE_TL = 2
private const val HANDLE_TR = 3
private const val HANDLE_BL = 4
private const val HANDLE_BR = 5

/** ContentScale.Fit ile gösterilen resmin ekrandaki dikdörtgeni: [x, y, w, h] */
private fun imageArea(viewW: Float, viewH: Float, aspect: Float): FloatArray {
    if (viewW <= 0f || viewH <= 0f || aspect <= 0f) return floatArrayOf(0f, 0f, viewW, viewH)
    val viewAspect = viewW / viewH
    return if (aspect > viewAspect) {
        val h = viewW / aspect
        floatArrayOf(0f, (viewH - h) / 2f, viewW, h)
    } else {
        val w = viewH * aspect
        floatArrayOf((viewW - w) / 2f, 0f, w, viewH)
    }
}

private fun handleAt(x: Float, y: Float, crop: CropRect, area: FloatArray): Int {
    val left = area[0] + crop.left * area[2]
    val top = area[1] + crop.top * area[3]
    val right = area[0] + crop.right * area[2]
    val bottom = area[1] + crop.bottom * area[3]
    val threshold = 64f
    fun near(px: Float, py: Float) = abs(x - px) < threshold && abs(y - py) < threshold
    return when {
        near(left, top) -> HANDLE_TL
        near(right, top) -> HANDLE_TR
        near(left, bottom) -> HANDLE_BL
        near(right, bottom) -> HANDLE_BR
        x in left..right && y in top..bottom -> HANDLE_MOVE
        else -> HANDLE_NONE
    }
}

private fun moveCrop(crop: CropRect, handle: Int, dx: Float, dy: Float): CropRect {
    val minSize = 0.08f
    return when (handle) {
        HANDLE_MOVE -> {
            val width = crop.right - crop.left
            val height = crop.bottom - crop.top
            val left = (crop.left + dx).coerceIn(0f, 1f - width)
            val top = (crop.top + dy).coerceIn(0f, 1f - height)
            CropRect(left, top, left + width, top + height)
        }

        HANDLE_TL -> crop.copy(
            left = min(crop.left + dx, crop.right - minSize).coerceAtLeast(0f),
            top = min(crop.top + dy, crop.bottom - minSize).coerceAtLeast(0f)
        )

        HANDLE_TR -> crop.copy(
            right = max(crop.right + dx, crop.left + minSize).coerceAtMost(1f),
            top = min(crop.top + dy, crop.bottom - minSize).coerceAtLeast(0f)
        )

        HANDLE_BL -> crop.copy(
            left = min(crop.left + dx, crop.right - minSize).coerceAtLeast(0f),
            bottom = max(crop.bottom + dy, crop.top + minSize).coerceAtMost(1f)
        )

        HANDLE_BR -> crop.copy(
            right = max(crop.right + dx, crop.left + minSize).coerceAtMost(1f),
            bottom = max(crop.bottom + dy, crop.top + minSize).coerceAtMost(1f)
        )

        else -> crop
    }
}
