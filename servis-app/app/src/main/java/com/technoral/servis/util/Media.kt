package com.technoral.servis.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

private const val MAX_EDGE = 1600

/** Kamera çekimi için geçici dosya + FileProvider adresi. */
fun createCameraTarget(context: Context, dir: File): Pair<File, Uri> {
    dir.mkdirs()
    val file = File(dir, "IMG_${fileStamp()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return file to uri
}

fun shareUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * Seçilen/çekilen görseli küçültüp uygulama klasörüne kaydeder.
 * Telefon hafızasını şişirmemek ve mail eklerini makul tutmak için uzun kenar 1600px.
 */
fun importImage(context: Context, source: Uri, targetDir: File): File? = runCatching {
    targetDir.mkdirs()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > MAX_EDGE * 2) sample *= 2

    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = context.contentResolver.openInputStream(source)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return@runCatching null

    val rotated = context.contentResolver.openInputStream(source)?.use { stream ->
        val exif = ExifInterface(stream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        applyOrientation(decoded, orientation)
    } ?: decoded

    val scaled = downscale(rotated)
    val out = File(targetDir, "IMG_${fileStamp()}_${(0..999).random()}.jpg")
    FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    if (scaled != rotated) scaled.recycle()
    if (rotated != decoded) rotated.recycle()
    decoded.recycle()
    out
}.getOrNull()

/** Kamera dosyası zaten diskte; sadece küçültüp yerine yazar. */
fun compressInPlace(file: File) = runCatching {
    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@runCatching
    val orientation = ExifInterface(file.absolutePath)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val rotated = applyOrientation(decoded, orientation)
    val scaled = downscale(rotated)
    FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    if (scaled != rotated) scaled.recycle()
    if (rotated != decoded) rotated.recycle()
    decoded.recycle()
}

private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return src
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
}

private fun downscale(src: Bitmap): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= MAX_EDGE) return src
    val ratio = MAX_EDGE.toFloat() / longest
    return Bitmap.createScaledBitmap(
        src,
        (src.width * ratio).toInt().coerceAtLeast(1),
        (src.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}

/** PDF/önizleme için ölçekli bitmap okur. */
fun loadBitmap(path: String, maxEdge: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return@runCatching null
    var sample = 1
    while (longest / sample > maxEdge * 2) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
