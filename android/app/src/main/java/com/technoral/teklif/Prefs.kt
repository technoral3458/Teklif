package com.technoral.teklif

import android.content.Context

/** Uygulamanin acacagi site adresini saklar. */
object Prefs {

    private const val FILE = "teklif_prefs"
    private const val KEY_SITE_URL = "site_url"

    private fun store(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Kayitli adres yoksa derleme sirasinda gomulen varsayilan adrese duser. */
    fun siteUrl(context: Context): String {
        val saved = store(context).getString(KEY_SITE_URL, null)
        if (!saved.isNullOrBlank()) return saved
        return context.getString(R.string.default_site_url).trim()
    }

    fun hasSiteUrl(context: Context): Boolean = siteUrl(context).isNotBlank()

    fun setSiteUrl(context: Context, url: String) {
        store(context).edit().putString(KEY_SITE_URL, url.trim()).apply()
    }

    /**
     * Kullanicinin yazdigi adresi kullanilabilir bir URL'e cevirir.
     * "site.com" -> "https://site.com", bos/gecersiz girdide null doner.
     */
    fun normalizeUrl(input: String): String? {
        var value = input.trim()
        if (value.isEmpty()) return null
        if (!value.contains("://")) value = "https://$value"
        val uri = runCatching { android.net.Uri.parse(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return value.trimEnd('/')
    }
}
