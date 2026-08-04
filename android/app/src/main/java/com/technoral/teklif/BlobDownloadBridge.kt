package com.technoral.teklif

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONObject

/**
 * Tarayici tarafinda uretilen (blob:) dosyalar Android'in indirme mekanizmasina
 * takilmaz. Bu koprü, blob icerigini sayfa icinde base64'e cevirip uygulamaya
 * geri gonderir; boylece olusturulan teklif PDF'i telefona kaydedilebilir.
 */
class BlobDownloadBridge(private val context: Context) {

    companion object {
        const val NAME = "AndroidBlobBridge"

        /** Verilen blob adresini sayfa icinde okuyup kopruye ileten betik. */
        fun fetchScript(blobUrl: String, mimeType: String?, fileName: String?): String {
            val url = JSONObject.quote(blobUrl)
            val mime = JSONObject.quote(mimeType.orEmpty())
            val name = JSONObject.quote(fileName.orEmpty())
            return """
                (function() {
                  try {
                    var xhr = new XMLHttpRequest();
                    xhr.open('GET', $url, true);
                    xhr.responseType = 'blob';
                    xhr.onload = function() {
                      if (this.status !== 200 && this.status !== 0) {
                        $NAME.onBlobError();
                        return;
                      }
                      var blob = this.response;
                      var reader = new FileReader();
                      reader.onloadend = function() {
                        $NAME.onBlobData(String(reader.result), blob.type || $mime, $name);
                      };
                      reader.onerror = function() { $NAME.onBlobError(); };
                      reader.readAsDataURL(blob);
                    };
                    xhr.onerror = function() { $NAME.onBlobError(); };
                    xhr.send();
                  } catch (e) {
                    $NAME.onBlobError();
                  }
                })();
            """.trimIndent()
        }
    }

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onBlobData(dataUrl: String, mimeType: String, fileName: String) {
        val separator = dataUrl.indexOf(BASE64_MARKER)
        if (!dataUrl.startsWith("data:") || separator < 0) {
            onBlobError()
            return
        }
        val header = dataUrl.substring(5, separator)
        val payload = dataUrl.substring(separator + BASE64_MARKER.length)
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
        if (bytes == null) {
            onBlobError()
            return
        }

        val resolvedMime = mimeType.ifBlank { header.ifBlank { "application/octet-stream" } }
        val resolvedName = fileName.ifBlank { null }
        main.post { Downloads.saveBytes(context, bytes, resolvedMime, resolvedName) }
    }

    @JavascriptInterface
    fun onBlobError() {
        main.post {
            Toast.makeText(context, R.string.download_failed, Toast.LENGTH_LONG).show()
        }
    }
}

private const val BASE64_MARKER = ";base64,"
