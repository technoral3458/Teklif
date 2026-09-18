package com.teknoral.parametrik.data.media

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class UriMeta(val displayName: String, val size: Long)

@Singleton
class FileMetaReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun read(uri: Uri): UriMeta {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "dosya"
        var size = -1L
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !it.isNull(nameIdx)) name = it.getString(nameIdx)
                val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !it.isNull(sizeIdx)) size = it.getLong(sizeIdx)
            }
        }
        if (size < 0) {
            size = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
        }
        return UriMeta(name, size)
    }
}
