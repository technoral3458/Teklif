package com.teknoral.parametrik.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

/**
 * Belirli (determinate) ilerleme bildiren gövde. Dosyalar büyük olabildiği için
 * yükleme sırasında yüzde göstermek şart.
 */
class ProgressRequestBody(
    private val contentType: MediaType?,
    private val contentLength: Long,
    private val openStream: () -> InputStream,
    private val onProgress: (sent: Long, total: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentLength

    override fun writeTo(sink: BufferedSink) {
        openStream().use { input ->
            val source = input.source()
            val buffer = okio.Buffer()
            var sent = 0L
            var lastReported = 0L
            while (true) {
                val read = source.read(buffer, CHUNK)
                if (read == -1L) break
                sink.write(buffer, read)
                sent += read
                if (sent - lastReported >= REPORT_STEP || sent == contentLength) {
                    lastReported = sent
                    onProgress(sent, contentLength)
                }
            }
            onProgress(contentLength, contentLength)
        }
    }

    private companion object {
        const val CHUNK = 64 * 1024L
        const val REPORT_STEP = 128 * 1024L
    }
}
