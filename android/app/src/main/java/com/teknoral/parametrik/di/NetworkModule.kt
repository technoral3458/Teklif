package com.teknoral.parametrik.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.teknoral.parametrik.BuildConfig
import com.teknoral.parametrik.data.local.PersistentCookieJar
import com.teknoral.parametrik.data.remote.BaseUrlInterceptor
import com.teknoral.parametrik.data.remote.ParametricApi
import com.teknoral.parametrik.data.remote.ServerConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: PersistentCookieJar,
        baseUrlInterceptor: BaseUrlInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        // Sunucu 303 ile konuşuyor: başarı/hata bilgisi Location başlığında.
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(cookieJar)
        .addInterceptor(baseUrlInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
                )
            }
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(ServerConfig.PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideParametricApi(retrofit: Retrofit): ParametricApi =
        retrofit.create(ParametricApi::class.java)
}
