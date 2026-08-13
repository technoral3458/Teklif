package com.technoral.tvkumanda.protocol

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket

/** TV'den gelen durum bildirimleri. */
interface RemoteListener {
    /** El sikisma bitti, tus gonderilebilir. */
    fun onReady() {}

    /** TV ses seviyesini bildirdi. */
    fun onVolume(level: Int, max: Int, muted: Boolean) {}

    /** TV'de on planda olan uygulamanin paket adi. */
    fun onCurrentApp(packageName: String) {}

    /** Baglanti kapandi. [error] null ise normal kapanis. */
    fun onDisconnected(error: Throwable?) {}
}

/**
 * Android TV Remote v2 kumanda kanali - TCP/TLS 6466.
 *
 * Bu kanal yalnizca daha once [PairingClient] ile eslesmis bir istemci
 * sertifikasi sunuldugunda acilir; aksi halde TV el sikismayi keser.
 *
 * TV duzenli olarak ping gonderir ve yanit alamazsa baglantiyi dusurur, bu
 * yuzden okuma dongusu surekli calisir.
 */
class RemoteClient(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val client: ClientCertificate,
    private val expectedFingerprint: String?,
    private val deviceModel: String,
    private val deviceVendor: String,
    private val appVersion: String,
    private val listener: RemoteListener,
) : Closeable {

    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val writeLock = Any()

    @Volatile
    private var closed = false

    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * Baglanir ve baglanti kapanana kadar bloklar. Arka plan is parcaciginda
     * cagrilmali.
     */
    fun run() {
        var failure: Throwable? = null
        try {
            openSocket()
            readLoop()
        } catch (t: Throwable) {
            if (!closed) failure = t
        } finally {
            isReady = false
            runCatching { socket?.close() }
            listener.onDisconnected(failure)
        }
    }

    private fun openSocket() {
        val trustManager = PinningTrustManager(expectedFingerprint)
        val context = Tls.contextFor(client, trustManager)
        // Adres apply blogunun disinda hesaplaniyor: blok icinde `port`,
        // Socket.getPort() ile golgelenir ve baglanmamis sokette 0 doner.
        val address = InetSocketAddress(host, port)
        val sslSocket = (context.socketFactory.createSocket() as SSLSocket).apply {
            Tls.configure(this)
            connect(address, CONNECT_TIMEOUT_MS)
            startHandshake()
        }
        socket = sslSocket
        input = sslSocket.inputStream
        output = sslSocket.outputStream
    }

    private fun readLoop() {
        val stream = input ?: return
        while (!closed) {
            val raw = MessageFraming.read(stream) ?: return
            handle(ProtoMessage.parse(raw))
        }
    }

    private fun handle(message: ProtoMessage) {
        message.message(FIELD_CONFIGURE)?.let {
            send(configureResponse())
            return
        }
        message.message(FIELD_SET_ACTIVE)?.let {
            send(setActiveResponse())
            return
        }
        message.message(FIELD_PING_REQUEST)?.let { ping ->
            send(pingResponse(ping.int(1) ?: 0))
            if (!isReady) markReady()
            return
        }
        message.message(FIELD_START)?.let { start ->
            if (start.bool(1) != false) markReady()
            return
        }
        message.message(FIELD_SET_VOLUME_LEVEL)?.let { volume ->
            listener.onVolume(
                level = volume.int(5) ?: 0,
                max = volume.int(4) ?: 0,
                muted = volume.bool(6) ?: false,
            )
            return
        }
        message.message(FIELD_IME_KEY_INJECT)?.let { ime ->
            ime.message(1)?.string(1)?.takeIf { it.contains('.') }?.let(listener::onCurrentApp)
            return
        }
        message.message(FIELD_ERROR)?.let {
            // TV bir istegi reddetti; baglanti acik kaldigi icin sadece yok sayiyoruz.
            return
        }
    }

    private fun markReady() {
        if (!isReady) {
            isReady = true
            listener.onReady()
        }
    }

    // --- Gonderilen komutlar -------------------------------------------------

    /** Tek dokunusluk tus gonderir. */
    fun sendKey(keyCode: Int) = send(keyInject(DIRECTION_SHORT, keyCode))

    /** Uzun basma baslatir; [endLongPress] ile bitirilmeli. */
    fun startLongPress(keyCode: Int) = send(keyInject(DIRECTION_START_LONG, keyCode))

    fun endLongPress(keyCode: Int) = send(keyInject(DIRECTION_END_LONG, keyCode))

    /**
     * TV'de bir uygulama veya derin baglanti acar.
     * Ornek: `https://www.youtube.com`, `market://launch?id=com.netflix.ninja`
     */
    fun launchApp(link: String) = send(
        ProtoWriter().message(FIELD_APP_LINK_LAUNCH) { string(1, link) }.toByteArray(),
    )

    /** Metni tus tus gonderir; TV'de bir yazi alani odakta olmali. */
    fun sendText(text: String) {
        text.forEach { char ->
            val keyCode = TvKeys.keyCodeForChar(char) ?: return@forEach
            sendKey(keyCode)
        }
    }

    private fun send(payload: ByteArray) {
        val out = output ?: throw IllegalStateException("Kumanda bağlantısı kapalı")
        synchronized(writeLock) { MessageFraming.write(out, payload) }
    }

    override fun close() {
        closed = true
        isReady = false
        runCatching { socket?.close() }
    }

    // --- Mesaj olusturucular -------------------------------------------------

    private fun configureResponse() = ProtoWriter()
        .message(FIELD_CONFIGURE) {
            int32(1, MAGIC_CODE)
            message(2) { // RemoteDeviceInfo
                string(1, deviceModel)
                string(2, deviceVendor)
                int32(3, 1)
                string(4, "1")
                string(5, PACKAGE_NAME)
                string(6, appVersion)
            }
        }
        .toByteArray()

    private fun setActiveResponse() = ProtoWriter()
        .message(FIELD_SET_ACTIVE) { int32(1, MAGIC_CODE) }
        .toByteArray()

    private fun pingResponse(value: Int) = ProtoWriter()
        .message(FIELD_PING_RESPONSE) { int32(1, value) }
        .toByteArray()

    private fun keyInject(direction: Int, keyCode: Int) = ProtoWriter()
        .message(FIELD_KEY_INJECT) {
            int32(1, direction)
            int32(2, keyCode)
        }
        .toByteArray()

    companion object {
        const val DEFAULT_PORT = 6466

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val PACKAGE_NAME = "com.technoral.tvkumanda"

        /** Protokolun bekledigi sabit el sikisma degeri. */
        private const val MAGIC_CODE = 622

        // RemoteMessage alanlari
        private const val FIELD_CONFIGURE = 1
        private const val FIELD_SET_ACTIVE = 2
        private const val FIELD_ERROR = 3
        private const val FIELD_PING_REQUEST = 8
        private const val FIELD_PING_RESPONSE = 9
        private const val FIELD_KEY_INJECT = 10
        private const val FIELD_IME_KEY_INJECT = 20
        private const val FIELD_START = 40
        private const val FIELD_SET_VOLUME_LEVEL = 50
        private const val FIELD_APP_LINK_LAUNCH = 90

        // RemoteDirection
        private const val DIRECTION_START_LONG = 1
        private const val DIRECTION_END_LONG = 2
        private const val DIRECTION_SHORT = 3
    }
}
