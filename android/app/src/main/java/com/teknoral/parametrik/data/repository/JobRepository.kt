package com.teknoral.parametrik.data.repository

import android.content.Context
import android.net.Uri
import com.teknoral.parametrik.core.AuthRequiredException
import com.teknoral.parametrik.core.ServerMessageException
import com.teknoral.parametrik.core.UnexpectedResponseException
import com.teknoral.parametrik.core.ValidationException
import com.teknoral.parametrik.core.apiCall
import com.teknoral.parametrik.data.media.CropRect
import com.teknoral.parametrik.data.media.DownloadNotifier
import com.teknoral.parametrik.data.media.DownloadStore
import com.teknoral.parametrik.data.media.FileMetaReader
import com.teknoral.parametrik.data.media.ImagePreparer
import com.teknoral.parametrik.data.media.SavedFile
import com.teknoral.parametrik.data.remote.Http
import com.teknoral.parametrik.data.remote.ParametricApi
import com.teknoral.parametrik.data.remote.ProgressRequestBody
import com.teknoral.parametrik.data.remote.ServerConfig
import com.teknoral.parametrik.data.remote.dto.toDomain
import com.teknoral.parametrik.domain.model.Geometry
import com.teknoral.parametrik.domain.model.ImageSliceParams
import com.teknoral.parametrik.domain.model.Job
import com.teknoral.parametrik.domain.model.JobDetail
import com.teknoral.parametrik.domain.model.MeshSliceParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadKind(val mime: String, val extension: String, val label: String) {
    DXF(DownloadStore.MIME_DXF, "dxf", "DXF"),
    ZIP(DownloadStore.MIME_ZIP, "zip", "ZIP")
}

sealed interface UploadState {
    data class Progress(val sent: Long, val total: Long) : UploadState {
        val fraction: Float get() = if (total > 0) (sent.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    data class Preparing(val message: String) : UploadState
    data class Completed(val jobId: Long) : UploadState
}

@Singleton
class JobRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ParametricApi,
    private val serverConfig: ServerConfig,
    private val fileMeta: FileMetaReader,
    private val imagePreparer: ImagePreparer,
    private val downloadStore: DownloadStore,
    private val notifier: DownloadNotifier
) {

    // ---------------------------------------------------------------- listeler

    suspend fun jobs(): Result<List<Job>> = apiCall {
        val response = decodeGuard { api.jobs() }
        Http.requireJsonBody(response, "/parametric/api/jobs").map { it.toDomain() }
    }

    suspend fun jobDetail(jobId: Long): Result<JobDetail> = apiCall {
        val response = decodeGuard { api.job(jobId) }
        Http.requireJsonBody(response, "/parametric/api/jobs/{id}").toDomain()
    }

    suspend fun geometry(jobId: Long): Result<Geometry> = apiCall {
        val response = decodeGuard { api.geometry(jobId) }
        Http.requireJsonBody(response, "/parametric/{id}/3d").toDomain()
    }

    // ---------------------------------------------------------------- yükleme

    /** .stl / .obj — en fazla 60 MB. */
    fun uploadMesh(jobName: String, uri: Uri): Flow<UploadState> = channelFlow {
        val meta = fileMeta.read(uri)
        val extension = meta.displayName.substringAfterLast('.', "").lowercase()
        if (extension !in MESH_EXTENSIONS) {
            throw ValidationException("Yalnızca .stl ve .obj dosyaları yüklenebilir.")
        }
        if (meta.size > MAX_MESH_BYTES) {
            throw ValidationException("Dosya çok büyük (${formatMb(meta.size)}). En fazla 60 MB yüklenebilir.")
        }

        var cached: File? = null
        val length: Long
        val openStream: () -> InputStream
        if (meta.size > 0) {
            length = meta.size
            openStream = {
                context.contentResolver.openInputStream(uri) ?: error("Dosya okunamadı.")
            }
        } else {
            send(UploadState.Preparing("Dosya hazırlanıyor…"))
            val copy = copyToCache(uri, meta.displayName)
            cached = copy
            if (copy.length() > MAX_MESH_BYTES) {
                copy.delete()
                throw ValidationException("Dosya çok büyük. En fazla 60 MB yüklenebilir.")
            }
            length = copy.length()
            openStream = { copy.inputStream() }
        }

        try {
            val body = ProgressRequestBody(OCTET_STREAM, length, openStream) { sent, total ->
                trySend(UploadState.Progress(sent, total))
            }
            val part = MultipartBody.Part.createFormData("file", meta.displayName, body)
            val response = api.uploadMesh(textPart(jobName), part)
            send(UploadState.Completed(jobIdOf(response)))
        } finally {
            cached?.delete()
        }
    }.flowOn(Dispatchers.IO)

    /** png/jpg/jpeg/webp/bmp/gif — en fazla 20 MB. Yüklemeden önce küçültülür. */
    fun uploadImage(jobName: String, uri: Uri, crop: CropRect = CropRect.FULL): Flow<UploadState> =
        channelFlow {
            send(UploadState.Preparing("Fotoğraf hazırlanıyor…"))
            val prepared = imagePreparer.prepare(uri, crop)
            if (prepared.length() > MAX_IMAGE_BYTES) {
                prepared.delete()
                throw ValidationException("Resim çok büyük. En fazla 20 MB yüklenebilir.")
            }
            try {
                val displayName = fileMeta.read(uri).displayName
                    .substringBeforeLast('.', "resim")
                    .ifBlank { "resim" } + ".jpg"
                val body = ProgressRequestBody(JPEG, prepared.length(), { prepared.inputStream() }) { sent, total ->
                    trySend(UploadState.Progress(sent, total))
                }
                val part = MultipartBody.Part.createFormData("file", displayName, body)
                val response = api.uploadImage(textPart(jobName), part)
                send(UploadState.Completed(jobIdOf(response)))
            } finally {
                prepared.delete()
            }
        }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- dilimleme

    /** @return sunucunun `msg=` mesajı (ör. "50 panel hazırlandı.") */
    suspend fun slice(jobId: Long, params: MeshSliceParams): Result<String?> = apiCall {
        Http.requireRedirectSuccess(api.slice(jobId, params.toFields()))
    }

    suspend fun sliceImage(jobId: Long, params: ImageSliceParams): Result<String?> = apiCall {
        Http.requireRedirectSuccess(api.sliceImage(jobId, params.toFields()))
    }

    // ---------------------------------------------------------------- indirme

    suspend fun download(jobId: Long, jobName: String, kind: DownloadKind): Result<SavedFile> = apiCall {
        val response = when (kind) {
            DownloadKind.DXF -> api.dxf(jobId)
            DownloadKind.ZIP -> api.zip(jobId)
        }

        if (Http.isRedirect(response.code())) {
            // Dilimlenmemiş iş: 303 + err=... döner, binary sanmayalım.
            Http.requireRedirectSuccess(response)
            throw ServerMessageException("Önce 'Dilimle' butonuna basın.")
        }
        if (Http.isLoginRedirect(Http.location(response))) throw AuthRequiredException()
        if (!response.isSuccessful) throw UnexpectedResponseException(response.code())

        val body = response.body() ?: throw UnexpectedResponseException(response.code())
        val fallbackName = "${slug(jobName)}_$jobId.${kind.extension}"
        val fileName = Http.fileNameFrom(response, fallbackName)

        val saved = body.byteStream().use { input ->
            downloadStore.save(fileName, kind.mime) { output -> input.copyTo(output) }
        }
        notifier.notifyDownloaded(saved)
        saved
    }

    // ---------------------------------------------------------------- diğer

    suspend fun delete(jobId: Long): Result<String?> = apiCall {
        Http.requireRedirectSuccess(api.delete(jobId))
    }

    /** Kaynak resmin adresi (Coil oturum çerezini paylaşan istemciyi kullanır). */
    fun imageUrl(jobId: Long): String = serverConfig.resolve("parametric/$jobId/image").toString()

    // ---------------------------------------------------------------- yardımcılar

    private fun jobIdOf(response: retrofit2.Response<okhttp3.ResponseBody>): Long {
        Http.requireRedirectSuccess(response)
        return Http.jobIdFrom(Http.location(response))
            ?: throw ServerMessageException("Sunucu iş numarası döndürmedi. Yükleme tamamlanamadı.")
    }

    private fun textPart(value: String) = value.trim().toRequestBody(TEXT_PLAIN)

    private fun copyToCache(uri: Uri, displayName: String): File {
        val dir = File(context.cacheDir, "yukleme").apply { mkdirs() }
        val target = File(dir, "model_${System.currentTimeMillis()}_$displayName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Dosya okunamadı.")
        return target
    }

    private inline fun <T> decodeGuard(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw AuthRequiredException("Sunucu JSON yerine sayfa döndürdü. Oturumunuz düşmüş olabilir.")
    }

    private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / 1_048_576.0)

    private fun slug(value: String): String = value.trim()
        .replace(Regex("""\s+"""), "_")
        .replace(Regex("""[^\p{L}\p{N}._-]"""), "")
        .ifBlank { "is" }

    private companion object {
        val MESH_EXTENSIONS = setOf("stl", "obj")
        const val MAX_MESH_BYTES = 60L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        val JPEG = "image/jpeg".toMediaType()
        val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()
    }
}
