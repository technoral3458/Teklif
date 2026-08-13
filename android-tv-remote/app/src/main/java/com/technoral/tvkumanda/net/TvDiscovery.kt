package com.technoral.tvkumanda.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Ag uzerinde bulunan bir Android TV. */
data class DiscoveredTv(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * Yerel agda `_androidtvremote2._tcp` yayini yapan TV'leri arar.
 *
 * Not: Bazi ev aglarinda mDNS yayini yonlendirici tarafindan engellenir
 * (AP isolation / misafir agi). Bu durumda kullanici IP'yi elle girebilmeli.
 */
class TvDiscovery(private val context: Context) {

    fun discover(): Flow<List<DiscoveredTv>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("tvkumanda-mdns")?.apply {
            setReferenceCounted(true)
            runCatching { acquire() }
        }

        val found = LinkedHashMap<String, DiscoveredTv>()

        // NsdManager ayni anda yalnizca tek bir cozumleme isini guvenilir sekilde
        // yurutur; istekleri siraya aliyoruz.
        val pending = ConcurrentLinkedQueue<NsdServiceInfo>()
        val resolving = AtomicBoolean(false)

        fun resolveNext() {
            if (!resolving.compareAndSet(false, true)) return
            val next = pending.poll()
            if (next == null) {
                resolving.set(false)
                return
            }
            // resolveService API 34'te yerini registerServiceInfoCallback'e biraktı,
            // ama minSdk 26 oldugu icin desteklenen tek yol bu.
            @Suppress("DEPRECATION")
            nsdManager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Cozumleme basarisiz: ${serviceInfo.serviceName} ($errorCode)")
                        resolving.set(false)
                        resolveNext()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = serviceInfo.host?.hostAddress
                        if (host != null) {
                            found[serviceInfo.serviceName] = DiscoveredTv(
                                name = serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port.takeIf { it > 0 } ?: DEFAULT_REMOTE_PORT,
                            )
                            trySend(found.values.toList())
                        }
                        resolving.set(false)
                        resolveNext()
                    }
                },
            )
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Arama baslatilamadi ($errorCode)")
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                pending.add(serviceInfo)
                resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
                trySend(found.values.toList())
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
            runCatching { multicastLock?.release() }
        }
    }

    companion object {
        private const val TAG = "TvDiscovery"
        private const val SERVICE_TYPE = "_androidtvremote2._tcp."
        const val DEFAULT_REMOTE_PORT = 6466
    }
}
