package com.teknoral.parametrik

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.teknoral.parametrik.data.local.SettingsStore
import com.teknoral.parametrik.data.remote.ServerConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class ParametrikApp : Application(), ImageLoaderFactory {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var serverConfig: ServerConfig
    @Inject lateinit var okHttpClient: OkHttpClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // İlk istekten önce adres hazır olmalı: tek seferlik küçük bir okuma.
        runBlocking { serverConfig.update(settingsStore.baseUrl.first()) }
        appScope.launch {
            settingsStore.baseUrl.collect { serverConfig.update(it) }
        }
    }

    /** Coil, oturum çerezini taşıyan aynı istemciyi kullanmalı. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { okHttpClient }
        .crossfade(true)
        .build()
}
