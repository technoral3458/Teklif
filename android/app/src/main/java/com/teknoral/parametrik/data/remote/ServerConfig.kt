package com.teknoral.parametrik.data.remote

import com.teknoral.parametrik.core.ServerNotConfiguredException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kullanıcının girdiği sunucu adresini tutar. Retrofit sabit bir base URL ile
 * kurulur; gerçek adres [BaseUrlInterceptor] tarafından her istekte yazılır.
 */
@Singleton
class ServerConfig @Inject constructor() {

    @Volatile
    var baseUrl: HttpUrl? = null
        private set

    fun update(raw: String?) {
        baseUrl = raw?.takeIf { it.isNotBlank() }?.toHttpUrlOrNull()
    }

    fun require(): HttpUrl = baseUrl ?: throw ServerNotConfiguredException()

    /** Tam adres üretir: "parametric/12/image" -> "https://host/parametric/12/image" */
    fun resolve(path: String): HttpUrl =
        require().newBuilder().addEncodedPathSegments(path.trimStart('/')).build()

    companion object {
        /** Retrofit'in ihtiyaç duyduğu yer tutucu; gerçek istekte kullanılmaz. */
        const val PLACEHOLDER_BASE_URL = "http://parametrik.invalid/"
    }
}
