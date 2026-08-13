import com.technoral.tvkumanda.protocol.ClientCertificate
import com.technoral.tvkumanda.protocol.MessageFraming
import com.technoral.tvkumanda.protocol.PairingClient
import com.technoral.tvkumanda.protocol.PairingException
import com.technoral.tvkumanda.protocol.PinningTrustManager
import com.technoral.tvkumanda.protocol.ProtoMessage
import com.technoral.tvkumanda.protocol.ProtoWriter
import com.technoral.tvkumanda.protocol.RemoteClient
import com.technoral.tvkumanda.protocol.RemoteListener
import com.technoral.tvkumanda.protocol.Tls
import com.technoral.tvkumanda.protocol.TvApps
import com.technoral.tvkumanda.protocol.TvKeys
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProtoCodecTest {

    @Test
    fun `varint sinirlari dogru kodlanir`() {
        assertContentEquals(byteArrayOf(0), ProtoWriter.encodeVarint(0))
        assertContentEquals(byteArrayOf(127), ProtoWriter.encodeVarint(127))
        assertContentEquals(byteArrayOf(0x80.toByte(), 0x01), ProtoWriter.encodeVarint(128))
        assertContentEquals(byteArrayOf(0xAC.toByte(), 0x02), ProtoWriter.encodeVarint(300))
    }

    @Test
    fun `mesaj yaz-oku turu korur`() {
        val encoded = ProtoWriter()
            .int32(1, 2)
            .int32(2, 200)
            .message(10) {
                string(1, "androidtvremote2")
                string(2, "Pixel Test")
            }
            .toByteArray()

        val parsed = ProtoMessage.parse(encoded)
        assertEquals(2, parsed.int(1))
        assertEquals(200, parsed.int(2))
        val inner = assertNotNull(parsed.message(10))
        assertEquals("androidtvremote2", inner.string(1))
        assertEquals("Pixel Test", inner.string(2))
    }

    @Test
    fun `bilinmeyen alanlar atlanir`() {
        val encoded = ProtoWriter()
            .int32(3, 7)          // ilgilenmedigimiz varint
            .bytes(99, ByteArray(200) { 1 }) // 2 baytlik uzunluk oneki gerektiren alan
            .string(5, "son")
            .toByteArray()

        val parsed = ProtoMessage.parse(encoded)
        assertEquals("son", parsed.string(5))
        assertEquals(200, parsed.bytes(99)!!.size)
    }

    @Test
    fun `cerceveleme buyuk yuku bolunmus okumada toplar`() {
        val payload = ByteArray(5000) { (it % 251).toByte() }
        val buffer = ByteArrayOutputStream()
        MessageFraming.write(buffer, payload)

        val read = MessageFraming.read(ByteArrayInputStream(buffer.toByteArray()))
        assertContentEquals(payload, read)
    }

    @Test
    fun `akis bitince null doner`() {
        assertEquals(null, MessageFraming.read(ByteArrayInputStream(ByteArray(0))))
    }
}

class CertificateTest {

    @Test
    fun `uretilen sertifika gecerli X509 ve kendi imzasini dogrular`() {
        val client = ClientCertificate.generate("tvkumanda")
        client.certificate.checkValidity()
        client.certificate.verify(client.certificate.publicKey)
        assertEquals(3, client.certificate.version)
        assertEquals("CN=tvkumanda", client.certificate.subjectX500Principal.name)
        assertEquals("SHA256withRSA", client.certificate.sigAlgName)
    }

    @Test
    fun `modulus 2048 bit icin 256 bayt ve isaret bayti tasimaz`() {
        val client = ClientCertificate.generate("tvkumanda")
        assertEquals(256, client.modulusBytes().size)
        assertTrue(client.modulusBytes()[0].toInt() and 0xFF != 0)
        assertContentEquals(byteArrayOf(0x01, 0x00, 0x01), client.exponentBytes())
    }

    @Test
    fun `pkcs12 deposu tur donusunde ayni anahtari verir`() {
        val file = File.createTempFile("tvkumanda", ".p12").apply { delete() }
        val first = ClientCertificate.loadOrCreate(file)
        val second = ClientCertificate.loadOrCreate(file)
        assertEquals(first.certificate, second.certificate)
        assertContentEquals(first.privateKey.encoded, second.privateKey.encoded)
        file.delete()
    }
}

class TvKeysTest {

    @Test
    fun `karakter eslemesi turkce harfleri ascii karsiligina dusurur`() {
        assertEquals(29, TvKeys.keyCodeForChar('a'))
        assertEquals(54, TvKeys.keyCodeForChar('z'))
        assertEquals(7, TvKeys.keyCodeForChar('0'))
        assertEquals(62, TvKeys.keyCodeForChar(' '))
        assertEquals(TvKeys.keyCodeForChar('s'), TvKeys.keyCodeForChar('ş'))
        assertEquals(TvKeys.keyCodeForChar('i'), TvKeys.keyCodeForChar('ı'))
        assertEquals(null, TvKeys.keyCodeForChar('!'))
    }

    @Test
    fun `youtube arama baglantisi utf8 olarak kodlanir`() {
        assertEquals(
            "https://www.youtube.com/results?search_query=kedi+videolar%C4%B1",
            TvApps.youtubeSearch("kedi videoları"),
        )
    }
}

/** Gercek TV yerine, ayni protokolu konusan sahte bir sunucuya karsi test. */
class PairingFlowTest {

    @Test
    fun `dogru kod ile eslesme tamamlanir`() {
        val server = FakePairingServer()
        try {
            val client = ClientCertificate.generate("telefon")
            val pairing = PairingClient("127.0.0.1", server.port, client, "Test Telefon")
            pairing.use {
                it.startPairing()
                val code = server.awaitCode()
                it.submitCode(code)
            }
            assertTrue(server.awaitPaired(), "sunucu eslesmeyi onaylamali")
            assertNotNull(pairing.serverFingerprint)
        } finally {
            server.close()
        }
    }

    @Test
    fun `yanlis kod istemci tarafinda yakalanir`() {
        val server = FakePairingServer()
        try {
            val client = ClientCertificate.generate("telefon")
            PairingClient("127.0.0.1", server.port, client, "Test Telefon").use {
                it.startPairing()
                val real = server.awaitCode()
                // Kontrol baytini bozuyoruz - istemci TV'ye gondermeden reddetmeli.
                val broken = "%02X".format((real.substring(0, 2).toInt(16) + 1) and 0xFF) + real.substring(2)
                val error = assertFailsWith<PairingException> { it.submitCode(broken) }
                assertTrue(error.message!!.contains("Kod hatalı"))
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `kod bicimi dogrulanir`() {
        val server = FakePairingServer()
        try {
            val client = ClientCertificate.generate("telefon")
            PairingClient("127.0.0.1", server.port, client, "Test Telefon").use {
                it.startPairing()
                server.awaitCode()
                assertFailsWith<PairingException> { it.submitCode("12345") }
                assertFailsWith<PairingException> { it.submitCode("ZZZZZZ") }
            }
        } finally {
            server.close()
        }
    }
}

class RemoteFlowTest {

    @Test
    fun `el sikisma ping ve tus gonderimi`() {
        val server = FakeRemoteServer()
        val client = ClientCertificate.generate("telefon")
        val ready = CountDownLatch(1)
        val volumeSeen = CountDownLatch(1)
        val appSeen = CountDownLatch(1)
        var volume = Triple(-1, -1, false)
        var currentApp: String? = null

        val remote = RemoteClient(
            host = "127.0.0.1",
            port = server.port,
            client = client,
            expectedFingerprint = null,
            deviceModel = "Pixel",
            deviceVendor = "Google",
            appVersion = "1.0",
            listener = object : RemoteListener {
                override fun onReady() = ready.countDown()
                override fun onVolume(level: Int, max: Int, muted: Boolean) {
                    volume = Triple(level, max, muted)
                    volumeSeen.countDown()
                }
                override fun onCurrentApp(packageName: String) {
                    currentApp = packageName
                    appSeen.countDown()
                }
            },
        )

        val runner = thread { remote.run() }
        try {
            assertTrue(ready.await(15, TimeUnit.SECONDS), "el sikisma tamamlanmali")
            assertEquals(622, server.awaitConfigureCode())
            assertEquals(622, server.awaitSetActive())
            assertEquals(42, server.awaitPingEcho())

            assertTrue(volumeSeen.await(5, TimeUnit.SECONDS), "ses seviyesi bildirilmeli")
            assertEquals(Triple(12, 100, false), volume)

            assertTrue(appSeen.await(5, TimeUnit.SECONDS), "on plandaki uygulama bildirilmeli")
            assertEquals("com.google.android.youtube", currentApp)

            remote.sendKey(TvKeys.DPAD_RIGHT)
            assertEquals(3 to TvKeys.DPAD_RIGHT, server.awaitKey())

            remote.startLongPress(TvKeys.DPAD_DOWN)
            assertEquals(1 to TvKeys.DPAD_DOWN, server.awaitKey())
            remote.endLongPress(TvKeys.DPAD_DOWN)
            assertEquals(2 to TvKeys.DPAD_DOWN, server.awaitKey())

            remote.launchApp("https://www.youtube.com")
            assertEquals("https://www.youtube.com", server.awaitAppLink())

            remote.sendText("ab 1")
            assertEquals(3 to 29, server.awaitKey())
            assertEquals(3 to 30, server.awaitKey())
            assertEquals(3 to TvKeys.SPACE, server.awaitKey())
            assertEquals(3 to 8, server.awaitKey())
        } finally {
            remote.close()
            server.close()
            runner.join(5_000)
        }
    }
}

// --- Sahte TV sunuculari -----------------------------------------------------

private fun serverSocket(cert: ClientCertificate): SSLServerSocket {
    val context = Tls.contextFor(cert, PinningTrustManager(null))
    val loopback = java.net.InetAddress.getByName("127.0.0.1")
    return (context.serverSocketFactory.createServerSocket(0, 16, loopback) as SSLServerSocket).apply {
        needClientAuth = true
    }
}

private class FakePairingServer : AutoCloseable {
    private val cert = ClientCertificate.generate("sahte-tv")
    private val server = serverSocket(cert)
    val port: Int = server.localPort

    private val codeLatch = CountDownLatch(1)
    private val pairedLatch = CountDownLatch(1)
    private var code: String = ""

    private val worker = thread(isDaemon = true) { runCatching { serve() } }

    fun awaitCode(): String {
        check(codeLatch.await(15, TimeUnit.SECONDS)) { "sunucu kod uretmedi" }
        return code
    }

    fun awaitPaired(): Boolean = pairedLatch.await(15, TimeUnit.SECONDS)

    private fun serve() {
        val socket = server.accept() as SSLSocket
        socket.use {
            val input = it.inputStream
            val output = it.outputStream
            val clientKey = it.session.peerCertificates.first() as X509Certificate

            // 1) PairingRequest -> PairingRequestAck
            val request = read(input)
            require(request.message(10) != null) { "PairingRequest bekleniyordu" }
            write(output, ProtoWriter().int32(1, 2).int32(2, 200).message(11) { string(1, "Sahte TV") }.toByteArray())

            // 2) PairingOption -> PairingOption
            val option = read(input)
            val encoding = option.message(20)!!.message(1)!!
            require(encoding.int(1) == 3) { "onaltilik kodlama bekleniyordu" }
            require(encoding.int(2) == 6) { "6 karakter bekleniyordu" }
            write(
                output,
                ProtoWriter().int32(1, 2).int32(2, 200)
                    .message(20) { message(1) { int32(1, 3); int32(2, 6) }; int32(3, 1) }
                    .toByteArray(),
            )

            // 3) PairingConfiguration -> PairingConfigurationAck, ardindan kodu "ekranda goster"
            val configuration = read(input)
            require(configuration.message(30) != null) { "PairingConfiguration bekleniyordu" }
            write(output, ProtoWriter().int32(1, 2).int32(2, 200).message(31) { }.toByteArray())

            val nonce = ByteArray(2).also { n -> SecureRandom().nextBytes(n) }
            val expected = sharedSecret(clientKey, cert.certificate, nonce)
            code = "%02X".format(expected[0]) + nonce.joinToString("") { b -> "%02X".format(b) }
            codeLatch.countDown()

            // 4) PairingSecret -> PairingSecretAck
            val secret = read(input)
            val received = secret.message(40)!!.bytes(1)!!
            if (received.contentEquals(expected)) {
                write(output, ProtoWriter().int32(1, 2).int32(2, 200).message(41) { bytes(1, expected) }.toByteArray())
                pairedLatch.countDown()
            } else {
                write(output, ProtoWriter().int32(1, 2).int32(2, 402).message(41) { }.toByteArray())
            }
        }
    }

    private fun sharedSecret(client: X509Certificate, server: X509Certificate, nonce: ByteArray): ByteArray {
        val clientKey = client.publicKey as RSAPublicKey
        val serverKey = server.publicKey as RSAPublicKey
        return MessageDigest.getInstance("SHA-256").run {
            update(ClientCertificate.unsignedBytes(clientKey.modulus))
            update(ClientCertificate.unsignedBytes(clientKey.publicExponent))
            update(ClientCertificate.unsignedBytes(serverKey.modulus))
            update(ClientCertificate.unsignedBytes(serverKey.publicExponent))
            update(nonce)
            digest()
        }
    }

    override fun close() {
        runCatching { server.close() }
        worker.interrupt()
    }
}

private class FakeRemoteServer : AutoCloseable {
    private val cert = ClientCertificate.generate("sahte-tv")
    private val server = serverSocket(cert)
    val port: Int = server.localPort

    private val configureCode = java.util.concurrent.ArrayBlockingQueue<Int>(4)
    private val setActive = java.util.concurrent.ArrayBlockingQueue<Int>(4)
    private val pingEcho = java.util.concurrent.ArrayBlockingQueue<Int>(4)
    private val keys = java.util.concurrent.LinkedBlockingQueue<Pair<Int, Int>>()
    private val appLinks = java.util.concurrent.LinkedBlockingQueue<String>()

    private val worker = thread(isDaemon = true) { runCatching { serve() } }

    fun awaitConfigureCode(): Int = assertNotNull(configureCode.poll(15, TimeUnit.SECONDS))
    fun awaitSetActive(): Int = assertNotNull(setActive.poll(15, TimeUnit.SECONDS))
    fun awaitPingEcho(): Int = assertNotNull(pingEcho.poll(15, TimeUnit.SECONDS))
    fun awaitKey(): Pair<Int, Int> = assertNotNull(keys.poll(15, TimeUnit.SECONDS))
    fun awaitAppLink(): String = assertNotNull(appLinks.poll(15, TimeUnit.SECONDS))

    private fun serve() {
        val socket = server.accept() as SSLSocket
        socket.use {
            val input = it.inputStream
            val output = it.outputStream

            // TV once kendi yapilandirmasini gonderir.
            write(output, ProtoWriter().message(1) { int32(1, 622); message(2) { string(1, "Arcelik TV") } }.toByteArray())
            configureCode.put(read(input).message(1)!!.int(1)!!)

            write(output, ProtoWriter().message(2) { int32(1, 622) }.toByteArray())
            setActive.put(read(input).message(2)!!.int(1)!!)

            write(output, ProtoWriter().message(40) { bool(1, true) }.toByteArray())
            write(output, ProtoWriter().message(8) { int32(1, 42); int32(2, 1) }.toByteArray())
            pingEcho.put(read(input).message(9)!!.int(1)!!)

            write(
                output,
                ProtoWriter().message(50) {
                    int32(1, 0); int32(2, 0); string(3, "TV"); int32(4, 100); int32(5, 12); bool(6, false)
                }.toByteArray(),
            )
            write(
                output,
                ProtoWriter().message(20) { message(1) { string(1, "com.google.android.youtube") } }.toByteArray(),
            )

            while (true) {
                val message = read(input)
                message.message(10)?.let { key -> keys.put((key.int(1) ?: 0) to (key.int(2) ?: 0)) }
                message.message(90)?.let { link -> appLinks.put(link.string(1) ?: "") }
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
        worker.interrupt()
    }
}

private fun read(input: java.io.InputStream): ProtoMessage =
    ProtoMessage.parse(requireNotNull(MessageFraming.read(input)) { "baglanti kapandi" })

private fun write(output: java.io.OutputStream, payload: ByteArray) = MessageFraming.write(output, payload)
