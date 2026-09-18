package com.teknoral.parametrik.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kalıcı çerez kavanozu. Sunucu oturumu `session` çerezi ile taşıdığı ve çerez
 * 30 gün geçerli olduğu için uygulama kapatılsa da oturum korunur.
 */
@Singleton
class PersistentCookieJar @Inject constructor(
    @ApplicationContext context: Context
) : CookieJar {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private data class Entry(val origin: HttpUrl, val cookie: Cookie)

    /** anahtar: domain | path | ad */
    private val entries = ConcurrentHashMap<String, Entry>()

    init {
        prefs.getStringSet(KEY, emptySet())?.forEach { line ->
            val sep = line.indexOf(SEPARATOR)
            if (sep <= 0) return@forEach
            val origin = line.substring(0, sep).toHttpUrlOrNull() ?: return@forEach
            val cookie = Cookie.parse(origin, line.substring(sep + SEPARATOR.length)) ?: return@forEach
            if (cookie.expiresAt > System.currentTimeMillis()) {
                entries[keyOf(origin, cookie)] = Entry(origin, cookie)
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            val key = keyOf(url, cookie)
            if (cookie.expiresAt <= now) entries.remove(key) else entries[key] = Entry(url, cookie)
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        var dropped = false
        val result = ArrayList<Cookie>(entries.size)
        for ((key, entry) in entries) {
            if (entry.cookie.expiresAt <= now) {
                entries.remove(key)
                dropped = true
            } else if (entry.cookie.matches(url)) {
                result += entry.cookie
            }
        }
        if (dropped) persist()
        return result
    }

    /** Çıkışta ya da sunucu adresi değiştiğinde oturumu tamamen sil. */
    fun clear() {
        entries.clear()
        prefs.edit().remove(KEY).apply()
    }

    fun hasSessionCookie(): Boolean {
        val now = System.currentTimeMillis()
        return entries.values.any { it.cookie.name == SESSION_COOKIE && it.cookie.expiresAt > now }
    }

    private fun persist() {
        val lines = entries.values.map { "${it.origin}$SEPARATOR${it.cookie}" }.toSet()
        prefs.edit().putStringSet(KEY, lines).apply()
    }

    private fun keyOf(url: HttpUrl, cookie: Cookie): String =
        "${cookie.domain.ifEmpty { url.host }}|${cookie.path}|${cookie.name}"

    private companion object {
        const val PREFS = "cerezler"
        const val KEY = "cookies"
        const val SEPARATOR = "|~|"
        const val SESSION_COOKIE = "session"
    }
}
