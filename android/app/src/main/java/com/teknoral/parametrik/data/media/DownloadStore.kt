package com.teknoral.parametrik.data.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SavedFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)

/**
 * Android 10+ : MediaStore.Downloads (izin gerekmez).
 * Android 9 ve altı: klasik Downloads klasörü (WRITE_EXTERNAL_STORAGE gerekir).
 */
@Singleton
class DownloadStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun save(
        fileName: String,
        mimeType: String,
        writeBody: (OutputStream) -> Unit
    ): SavedFile = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(fileName, mimeType, writeBody)
        } else {
            saveLegacy(fileName, mimeType, writeBody)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(
        fileName: String,
        mimeType: String,
        writeBody: (OutputStream) -> Unit
    ): SavedFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUB_DIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Dosya kaydedilemedi.")
        try {
            resolver.openOutputStream(uri)?.use(writeBody) ?: error("Dosya yazılamadı.")
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return SavedFile(uri, fileName, mimeType)
    }

    private fun saveLegacy(
        fileName: String,
        mimeType: String,
        writeBody: (OutputStream) -> Unit
    ): SavedFile {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            SUB_DIR
        ).apply { mkdirs() }

        var target = File(dir, fileName)
        var index = 1
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        while (target.exists()) {
            val suffix = if (ext.isEmpty()) "" else ".$ext"
            target = File(dir, "$base ($index)$suffix")
            index++
        }

        FileOutputStream(target).use(writeBody)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType), null)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return SavedFile(uri, target.name, mimeType)
    }

    companion object {
        const val SUB_DIR = "Parametrik Kesim"
        const val MIME_DXF = "application/dxf"
        const val MIME_ZIP = "application/zip"
    }
}
