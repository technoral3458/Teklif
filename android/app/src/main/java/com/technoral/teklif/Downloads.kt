package com.technoral.teklif

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Teklif PDF'i gibi dosyalarin telefona inmesini saglar.
 * Sunucudan gelen normal baglantilar DownloadManager'a, tarayicida uretilen
 * blob:/data: icerikleri ise dogrudan Indirilenler klasorune yazilir.
 */
object Downloads {

    fun viaDownloadManager(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            userAgent?.let { addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setTitle(fileName)
            setDescription(context.getString(R.string.download_in_progress))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { manager.enqueue(request) }
            .onSuccess { toast(context, context.getString(R.string.download_started, fileName)) }
            .onFailure { toast(context, context.getString(R.string.download_failed)) }
    }

    /** Base64 icerigi Indirilenler klasorune yazar ve acilmasi icin bildirim/uyari gosterir. */
    fun saveBytes(context: Context, bytes: ByteArray, mimeType: String, suggestedName: String?) {
        val fileName = uniqueName(suggestedName, mimeType)
        val saved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(context, bytes, mimeType, fileName)
            } else {
                saveToPublicDir(context, bytes, fileName)
            }
        }.getOrNull()

        if (saved == null) {
            toast(context, context.getString(R.string.download_failed))
            return
        }
        toast(context, context.getString(R.string.download_saved, fileName))
        openFile(context, saved, mimeType)
    }

    private fun saveWithMediaStore(
        context: Context,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore kaydi olusturulamadi")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Dosya yazilamadi")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveToPublicDir(context: Context, bytes: ByteArray, fileName: String): Uri {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun openFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun uniqueName(suggestedName: String?, mimeType: String): String {
        if (!suggestedName.isNullOrBlank()) return suggestedName
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        return "teklif-${System.currentTimeMillis()}.$extension"
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
