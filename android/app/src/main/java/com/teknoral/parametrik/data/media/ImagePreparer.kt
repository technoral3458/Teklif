package com.teknoral.parametrik.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Normalleştirilmiş kırpma dikdörtgeni (0..1). */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isFull: Boolean get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    companion object {
        val FULL = CropRect(0f, 0f, 1f, 1f)
    }
}

/**
 * Telefon fotoğrafları çok büyük olabiliyor: yüklemeden önce en uzun kenarı
 * [MAX_EDGE] piksele indirip JPEG %[QUALITY] ile sıkıştırırız.
 */
@Singleton
class ImagePreparer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun prepare(source: Uri, crop: CropRect = CropRect.FULL): File = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        require(srcW > 0 && srcH > 0) { "Resim okunamadı." }

        val cropW = ((crop.right - crop.left).coerceIn(0.01f, 1f) * srcW)
        val cropH = ((crop.bottom - crop.top).coerceIn(0.01f, 1f) * srcH)
        val longestAfterCrop = max(cropW, cropH)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(longestAfterCrop.roundToInt(), MAX_EDGE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Resim çözümlenemedi.")

        bitmap = applyExifRotation(source, bitmap)

        if (!crop.isFull) {
            val x = (crop.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
            val y = (crop.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
            val w = ((crop.right - crop.left) * bitmap.width).roundToInt().coerceAtLeast(1)
                .coerceAtMost(bitmap.width - x)
            val h = ((crop.bottom - crop.top) * bitmap.height).roundToInt().coerceAtLeast(1)
                .coerceAtMost(bitmap.height - y)
            val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
            if (cropped != bitmap) bitmap.recycle()
            bitmap = cropped
        }

        val longest = max(bitmap.width, bitmap.height)
        if (longest > MAX_EDGE) {
            val ratio = MAX_EDGE.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                true
            )
            if (scaled != bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val target = File(context.cacheDir, "yukleme").apply { mkdirs() }
            .let { File(it, "resim_${System.currentTimeMillis()}.jpg") }
        FileOutputStream(target).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out) }
        bitmap.recycle()
        target
    }

    private fun applyExifRotation(source: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun sampleSizeFor(longestEdge: Int, target: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= target) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_EDGE = 2000
        const val QUALITY = 85
    }
}
