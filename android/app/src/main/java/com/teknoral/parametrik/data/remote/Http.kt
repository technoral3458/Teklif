package com.teknoral.parametrik.data.remote

import com.teknoral.parametrik.core.AuthRequiredException
import com.teknoral.parametrik.core.MissingEndpointException
import com.teknoral.parametrik.core.ServerMessageException
import com.teknoral.parametrik.core.UnexpectedResponseException
import retrofit2.Response
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * Sunucu HTML tabanlı: çoğu uç nokta 303 döner, sonuç `Location` başlığındaki
 * `err=` / `msg=` parametrelerinde taşınır. Değerler URL-encoded Türkçe metindir.
 */
object Http {

    fun isRedirect(code: Int): Boolean = code in 300..399

    fun location(response: Response<*>): String? = response.headers()["Location"]

    /** `Location: /parametric/12?msg=50%20panel...` içinden bir sorgu değerini çözer. */
    fun queryValue(location: String?, key: String): String? {
        if (location.isNullOrEmpty()) return null
        val query = location.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val name = pair.substringBefore('=')
            if (name != key) continue
            val raw = pair.substringAfter('=', "")
            return decode(raw)
        }
        return null
    }

    fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        value
    } catch (e: IllegalArgumentException) {
        value
    }

    /** `/parametric/12` ya da `/parametric/12?msg=...` içinden iş numarası. */
    fun jobIdFrom(location: String?): Long? {
        if (location.isNullOrEmpty()) return null
        val path = location.substringBefore('?')
        return JOB_ID.find(path)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    fun isLoginRedirect(location: String?): Boolean {
        if (location.isNullOrEmpty()) return false
        val path = location.substringBefore('?').trimEnd('/')
        return path.endsWith("/login") || path == "/login" || path.endsWith("/auth/login")
    }

    /** `Content-Disposition: attachment; filename="panel.dxf"` */
    fun fileNameFrom(response: Response<*>, fallback: String): String {
        val header = response.headers()["Content-Disposition"] ?: return fallback
        FILENAME_STAR.find(header)?.groupValues?.getOrNull(1)?.let { return decode(it).sanitizeFileName(fallback) }
        FILENAME.find(header)?.groupValues?.getOrNull(1)?.let { return it.sanitizeFileName(fallback) }
        return fallback
    }

    /**
     * Yönlendirme tabanlı bir yanıtı değerlendirir.
     * @return `msg=` değeri (yoksa null)
     * @throws ServerMessageException `err=` geldiyse
     * @throws AuthRequiredException oturum düştüyse
     */
    fun requireRedirectSuccess(response: Response<*>): String? {
        val location = location(response)
        if (isLoginRedirect(location)) throw AuthRequiredException()

        queryValue(location, "err")?.let { throw ServerMessageException(it) }

        if (!isRedirect(response.code())) {
            throw UnexpectedResponseException(response.code())
        }
        return queryValue(location, "msg")
    }

    /** JSON uç noktaları için ortak kontrol. */
    fun <T> requireJsonBody(response: Response<T>, endpoint: String): T {
        if (isLoginRedirect(location(response)) || response.code() == 401 || response.code() == 403) {
            throw AuthRequiredException()
        }
        if (response.code() == 404) throw MissingEndpointException(endpoint)
        if (!response.isSuccessful) throw UnexpectedResponseException(response.code())
        return response.body() ?: throw UnexpectedResponseException(response.code())
    }

    private val JOB_ID = Regex("""/parametric/(\d+)""")
    private val FILENAME_STAR = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
    private val FILENAME = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)

    private fun String.sanitizeFileName(fallback: String): String {
        val cleaned = trim().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[^\p{L}\p{N}._\- ]"""), "_")
            .trim()
        return cleaned.ifEmpty { fallback }
    }
}
