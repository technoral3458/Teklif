package com.teknoral.parametrik.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Yer tutucu base URL'i kullanıcının girdiği sunucu adresiyle değiştirir. */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val serverConfig: ServerConfig
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = serverConfig.require()

        // Adres zaten çözülmüşse (ör. Coil'in yüklediği resim URL'i) yeniden yazma.
        if (request.url.host == base.host && request.url.port == base.port) {
            return chain.proceed(request.newBuilder().header("Accept-Language", "tr-TR,tr;q=0.9").build())
        }

        val builder = base.newBuilder()
            .addEncodedPathSegments(request.url.encodedPath.trimStart('/'))
        request.url.encodedQuery?.let { builder.encodedQuery(it) }

        return chain.proceed(
            request.newBuilder()
                .url(builder.build())
                .header("Accept-Language", "tr-TR,tr;q=0.9")
                .build()
        )
    }
}
