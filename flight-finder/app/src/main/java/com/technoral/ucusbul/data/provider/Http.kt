package com.technoral.ucusbul.data.provider

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/** Sağlayıcı hatasını kullanıcıya gösterilebilir bir mesajla taşır. */
class ProviderException(message: String) : Exception(message)
