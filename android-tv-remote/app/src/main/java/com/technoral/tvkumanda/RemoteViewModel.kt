package com.technoral.tvkumanda

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.technoral.tvkumanda.data.DeviceStore
import com.technoral.tvkumanda.data.SavedTv
import com.technoral.tvkumanda.net.DiscoveredTv
import com.technoral.tvkumanda.net.TvDiscovery
import com.technoral.tvkumanda.protocol.ClientCertificate
import com.technoral.tvkumanda.protocol.PairingClient
import com.technoral.tvkumanda.protocol.PairingException
import com.technoral.tvkumanda.protocol.RemoteClient
import com.technoral.tvkumanda.protocol.RemoteListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Ekranin ne gostermesi gerektigini belirleyen baglanti durumu. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val deviceName: String) : ConnectionState

    /** TV ekraninda kod gosteriliyor, kullanicidan bekleniyor. */
    data class WaitingForCode(val deviceName: String, val error: String? = null) : ConnectionState

    data class Connected(val deviceName: String, val host: String) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class VolumeState(val level: Int, val max: Int, val muted: Boolean)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DeviceStore(application)
    private val discovery = TvDiscovery(application)
    private val certificateFile = File(application.filesDir, "client.p12")

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discovered: StateFlow<List<DiscoveredTv>> = _discovered.asStateFlow()

    private val _saved = MutableStateFlow(store.all())
    val saved: StateFlow<List<SavedTv>> = _saved.asStateFlow()

    private val _volume = MutableStateFlow<VolumeState?>(null)
    val volume: StateFlow<VolumeState?> = _volume.asStateFlow()

    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp: StateFlow<String?> = _currentApp.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var discoveryJob: Job? = null
    private var connectJob: Job? = null
    private var remote: RemoteClient? = null
    private var pairing: PairingClient? = null

    /** Eslesme sirasindaki hedef; kod dogrulaninca baglanti bununla kurulur. */
    private var pendingTarget: Target? = null

    private data class Target(val name: String, val host: String, val port: Int)

    private val clientName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    // --- Kesif ---------------------------------------------------------------

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            discovery.discover().collect { _discovered.value = it }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun refreshDiscovery() {
        stopDiscovery()
        _discovered.value = emptyList()
        startDiscovery()
    }

    // --- Baglanti ------------------------------------------------------------

    /**
     * Bir TV'ye baglanir. Cihazla daha once eslesilmediyse once eslesme akisi
     * baslatilir ve TV ekraninda kod gosterilir.
     */
    fun connect(name: String, host: String, port: Int = RemoteClient.DEFAULT_PORT) {
        disconnect()
        val target = Target(name, host, port)
        pendingTarget = target
        _connection.value = ConnectionState.Connecting(name)

        connectJob = ioScope.launch {
            val known = store.find(host)
            if (known == null) {
                beginPairing(target)
            } else {
                openRemote(target, known.fingerprint)
            }
        }
    }

    private suspend fun beginPairing(target: Target) {
        val client = certificate() ?: return
        val session = PairingClient(
            host = target.host,
            client = client,
            clientName = clientName,
        )
        pairing = session
        try {
            session.startPairing()
            _connection.value = ConnectionState.WaitingForCode(target.name)
        } catch (t: Throwable) {
            session.close()
            pairing = null
            _connection.value = ConnectionState.Failed(
                "TV ile eşleştirme başlatılamadı: ${t.readableMessage()}",
            )
        }
    }

    /** Kullanicinin TV ekranindan okudugu kodu dogrular. */
    fun submitCode(code: String) {
        val session = pairing ?: return
        val target = pendingTarget ?: return
        val name = target.name
        _connection.value = ConnectionState.Connecting(name)

        ioScope.launch {
            try {
                session.submitCode(code)
                val fingerprint = session.serverFingerprint.orEmpty()
                store.save(SavedTv(name, target.host, target.port, fingerprint))
                _saved.value = store.all()
                session.close()
                pairing = null
                openRemote(target, fingerprint)
            } catch (t: Throwable) {
                _connection.value = ConnectionState.WaitingForCode(name, t.readableMessage())
            }
        }
    }

    private fun openRemote(target: Target, fingerprint: String?) {
        val client = certificateBlocking() ?: return
        val listener = object : RemoteListener {
            override fun onReady() {
                store.lastHost = target.host
                _connection.value = ConnectionState.Connected(target.name, target.host)
            }

            override fun onVolume(level: Int, max: Int, muted: Boolean) {
                _volume.value = VolumeState(level, max, muted)
            }

            override fun onCurrentApp(packageName: String) {
                _currentApp.value = packageName
            }

            override fun onDisconnected(error: Throwable?) {
                remote = null
                _volume.value = null
                _connection.value = when {
                    error == null -> ConnectionState.Idle

                    // TV eslesmeyi unutmus ya da fabrika ayarlarina donmus olabilir.
                    // Kaydi siliyoruz ki listeden tekrar dokunuldugunda eslesme
                    // akisi bastan calissin.
                    error.looksLikeUnpaired() -> {
                        store.remove(target.host)
                        _saved.value = store.all()
                        ConnectionState.Failed(
                            "TV bu telefonu tanımıyor. Listeden TV'ye tekrar dokunun, " +
                                "ekranda yeni bir eşleştirme kodu çıkacak.",
                        )
                    }

                    else -> ConnectionState.Failed("Bağlantı koptu: ${error.readableMessage()}")
                }
            }
        }

        val session = RemoteClient(
            host = target.host,
            port = target.port,
            client = client,
            expectedFingerprint = fingerprint?.takeIf { it.isNotBlank() },
            deviceModel = Build.MODEL ?: "Telefon",
            deviceVendor = Build.MANUFACTURER ?: "Android",
            appVersion = "1.0",
            listener = listener,
        )
        remote = session
        session.run() // baglanti kapanana kadar bloklar (IO dispatcher'da)
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        remote?.close()
        remote = null
        pairing?.close()
        pairing = null
        _volume.value = null
        _connection.value = ConnectionState.Idle
    }

    fun cancelPairing() {
        pairing?.close()
        pairing = null
        pendingTarget = null
        _connection.value = ConnectionState.Idle
    }

    /** Baglanti kopmussa ayni cihaza yeniden baglanmayi dener. */
    fun reconnectIfNeeded() {
        val target = pendingTarget ?: return
        if (_connection.value is ConnectionState.Connected) return
        if (_connection.value is ConnectionState.WaitingForCode) return
        connect(target.name, target.host, target.port)
    }

    fun forget(host: String) {
        store.remove(host)
        _saved.value = store.all()
        if ((_connection.value as? ConnectionState.Connected)?.host == host) disconnect()
    }

    // --- Komutlar ------------------------------------------------------------

    val isConnected: Boolean get() = remote?.isReady == true

    fun sendKey(keyCode: Int) = onRemote { it.sendKey(keyCode) }

    fun startLongPress(keyCode: Int) = onRemote { it.startLongPress(keyCode) }

    fun endLongPress(keyCode: Int) = onRemote { it.endLongPress(keyCode) }

    fun launchApp(link: String) = onRemote { it.launchApp(link) }

    fun sendText(text: String) = onRemote { it.sendText(text) }

    private fun onRemote(block: (RemoteClient) -> Unit) {
        val session = remote ?: return
        ioScope.launch {
            runCatching { block(session) }.onFailure {
                _connection.value = ConnectionState.Failed("Komut gönderilemedi: ${it.readableMessage()}")
            }
        }
    }

    // --- Yardimcilar ---------------------------------------------------------

    private suspend fun certificate(): ClientCertificate? = withContext(Dispatchers.IO) {
        runCatching { ClientCertificate.loadOrCreate(certificateFile) }
            .onFailure {
                _connection.value = ConnectionState.Failed("Kimlik sertifikası oluşturulamadı: ${it.readableMessage()}")
            }
            .getOrNull()
    }

    /** Zaten IO dispatcher'inda oldugumuz cagrilar icin. */
    private fun certificateBlocking(): ClientCertificate? =
        runCatching { ClientCertificate.loadOrCreate(certificateFile) }
            .onFailure {
                _connection.value = ConnectionState.Failed("Kimlik sertifikası oluşturulamadı: ${it.readableMessage()}")
            }
            .getOrNull()

    override fun onCleared() {
        super.onCleared()
        remote?.close()
        pairing?.close()
        ioScope.cancel()
    }
}

private fun Throwable.readableMessage(): String = when (this) {
    is PairingException -> message ?: "eşleştirme hatası"
    is java.net.SocketTimeoutException -> "TV yanıt vermedi"
    is java.net.ConnectException -> "TV'ye ulaşılamadı (açık ve aynı ağda mı?)"
    is java.net.NoRouteToHostException -> "TV'ye ağ üzerinden ulaşılamıyor"
    else -> message ?: this::class.java.simpleName
}

/**
 * TV, tanimadigi bir istemci sertifikasi gorunce TLS el sikismasini keser.
 * Bu durumu "yeniden eslesmek gerekiyor" olarak yorumluyoruz.
 */
private fun Throwable.looksLikeUnpaired(): Boolean =
    this is javax.net.ssl.SSLException || this is javax.net.ssl.SSLHandshakeException
