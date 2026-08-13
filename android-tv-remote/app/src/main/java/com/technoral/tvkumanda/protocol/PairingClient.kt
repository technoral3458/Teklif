package com.technoral.tvkumanda.protocol

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/**
 * Android TV Remote v2 eslesme (pairing) kanali - TCP/TLS 6467.
 *
 * Akis:
 *  1. PairingRequest        ->  TV: PairingRequestAck
 *  2. PairingOption         ->  TV: PairingOption
 *  3. PairingConfiguration  ->  TV: PairingConfigurationAck  (TV ekranda 6 haneli kod gosterir)
 *  4. PairingSecret(hash)   ->  TV: PairingSecretAck         (eslesme tamam)
 *
 * Kod dogrulamasi cihazlar arasinda paylasilan bir sir uzerinden yapilir:
 * her iki tarafin acik anahtarlari + koddan gelen nonce SHA-256'dan gecirilir.
 */
class PairingClient(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val client: ClientCertificate,
    private val clientName: String,
    private val serviceName: String = "androidtvremote2",
) : Closeable {

    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var serverCertificate: X509Certificate? = null

    /** TV sertifikasinin SHA-256 parmak izi; eslesme bittikten sonra saklanmali. */
    var serverFingerprint: String? = null
        private set

    /**
     * Baglanir ve TV ekraninda kod gosterilene kadar olan adimlari yurutur.
     * Doner donmez kullanicidan kodu isteyip [submitCode] cagrilmalidir.
     */
    fun startPairing() {
        val trustManager = PinningTrustManager(expectedFingerprint = null)
        val context = Tls.contextFor(client, trustManager)
        // Adres apply blogunun disinda hesaplaniyor: blok icinde `port`,
        // Socket.getPort() ile golgelenir ve baglanmamis sokette 0 doner.
        val address = InetSocketAddress(host, port)
        val sslSocket = (context.socketFactory.createSocket() as SSLSocket).apply {
            Tls.configure(this)
            connect(address, CONNECT_TIMEOUT_MS)
            soTimeout = READ_TIMEOUT_MS
            startHandshake()
        }
        socket = sslSocket
        input = sslSocket.inputStream
        output = sslSocket.outputStream
        serverCertificate = sslSocket.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: error("TV sertifikası alınamadı")
        serverFingerprint = PinningTrustManager.fingerprintOf(serverCertificate!!)

        send(pairingRequest())
        expect(FIELD_PAIRING_REQUEST_ACK, "eşleştirme isteği reddedildi")

        send(pairingOption())
        expect(FIELD_PAIRING_OPTION, "eşleştirme seçenekleri reddedildi")

        send(pairingConfiguration())
        expect(FIELD_PAIRING_CONFIGURATION_ACK, "eşleştirme yapılandırması reddedildi")
        // Bu noktada TV ekraninda 6 karakterlik onaltilik kod goruntulenir.
    }

    /**
     * TV ekranindaki kodu dogrular. Kod hatali ise [PairingException] firlatir.
     * Basarili olursa TV telefonun sertifikasini kalici olarak kaydeder.
     */
    fun submitCode(code: String) {
        val normalized = code.trim().uppercase().replace(" ", "")
        if (!CODE_PATTERN.matches(normalized)) {
            throw PairingException("Kod 6 karakterlik onaltılık olmalı (örnek: 4A7B2C)")
        }
        val digest = secretFor(normalized)
        val expectedCheck = normalized.substring(0, 2).toInt(16)
        if ((digest[0].toInt() and 0xFF) != expectedCheck) {
            throw PairingException("Kod hatalı. TV ekranındaki kodu tekrar girin.")
        }

        send(pairingSecret(digest))
        expect(FIELD_PAIRING_SECRET_ACK, "TV kodu kabul etmedi")
    }

    /**
     * Paylasilan sir: SHA-256(istemci modulus || istemci exponent ||
     * sunucu modulus || sunucu exponent || nonce).
     *
     * nonce = kodun son 4 onaltilik karakteri (2 bayt).
     * Kodun ilk 2 karakteri ise ozetin ilk baytiyla karsilastirilan kontrol degeri.
     */
    private fun secretFor(code: String): ByteArray {
        val serverKey = (serverCertificate ?: error("Önce startPairing() çağrılmalı"))
            .publicKey as RSAPublicKey
        val nonce = hexToBytes(code.substring(2))

        return MessageDigest.getInstance("SHA-256").run {
            update(client.modulusBytes())
            update(client.exponentBytes())
            update(ClientCertificate.unsignedBytes(serverKey.modulus))
            update(ClientCertificate.unsignedBytes(serverKey.publicExponent))
            update(nonce)
            digest()
        }
    }

    private fun send(payload: ByteArray) {
        val out = output ?: throw PairingException("Eşleştirme bağlantısı kapalı")
        MessageFraming.write(out, payload)
    }

    /** Bir mesaj okur, durum kodunu kontrol eder ve beklenen alanin geldigini dogrular. */
    private fun expect(field: Int, failureMessage: String): ProtoMessage {
        val stream = input ?: throw PairingException("Eşleştirme bağlantısı kapalı")
        val raw = MessageFraming.read(stream)
            ?: throw PairingException("$failureMessage (TV bağlantıyı kapattı)")
        val message = ProtoMessage.parse(raw)

        val status = message.int(FIELD_STATUS) ?: STATUS_UNKNOWN
        if (status != STATUS_OK) {
            throw PairingException("$failureMessage (durum: ${statusText(status)})")
        }
        if (!message.has(field)) {
            throw PairingException("$failureMessage (beklenmeyen yanıt: $message)")
        }
        return message
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    // --- Mesaj olusturucular -------------------------------------------------

    private fun pairingRequest() = ProtoWriter()
        .int32(FIELD_PROTOCOL_VERSION, PROTOCOL_VERSION)
        .int32(FIELD_STATUS, STATUS_OK)
        .message(FIELD_PAIRING_REQUEST) {
            string(1, serviceName)
            string(2, clientName)
        }
        .toByteArray()

    private fun pairingOption() = ProtoWriter()
        .int32(FIELD_PROTOCOL_VERSION, PROTOCOL_VERSION)
        .int32(FIELD_STATUS, STATUS_OK)
        .message(FIELD_PAIRING_OPTION) {
            message(1) { // input_encodings
                int32(1, ENCODING_HEXADECIMAL)
                int32(2, CODE_LENGTH)
            }
            int32(3, ROLE_INPUT) // preferred_role
        }
        .toByteArray()

    private fun pairingConfiguration() = ProtoWriter()
        .int32(FIELD_PROTOCOL_VERSION, PROTOCOL_VERSION)
        .int32(FIELD_STATUS, STATUS_OK)
        .message(FIELD_PAIRING_CONFIGURATION) {
            message(1) { // encoding
                int32(1, ENCODING_HEXADECIMAL)
                int32(2, CODE_LENGTH)
            }
            int32(2, ROLE_INPUT) // client_role
        }
        .toByteArray()

    private fun pairingSecret(secret: ByteArray) = ProtoWriter()
        .int32(FIELD_PROTOCOL_VERSION, PROTOCOL_VERSION)
        .int32(FIELD_STATUS, STATUS_OK)
        .message(FIELD_PAIRING_SECRET) {
            bytes(1, secret)
        }
        .toByteArray()

    companion object {
        const val DEFAULT_PORT = 6467

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 30_000

        private const val PROTOCOL_VERSION = 2
        private const val CODE_LENGTH = 6
        private val CODE_PATTERN = Regex("^[0-9A-F]{6}$")

        // PairingMessage alanlari
        private const val FIELD_PROTOCOL_VERSION = 1
        private const val FIELD_STATUS = 2
        private const val FIELD_PAIRING_REQUEST = 10
        private const val FIELD_PAIRING_REQUEST_ACK = 11
        private const val FIELD_PAIRING_OPTION = 20
        private const val FIELD_PAIRING_CONFIGURATION = 30
        private const val FIELD_PAIRING_CONFIGURATION_ACK = 31
        private const val FIELD_PAIRING_SECRET = 40
        private const val FIELD_PAIRING_SECRET_ACK = 41

        private const val STATUS_UNKNOWN = 0
        private const val STATUS_OK = 200
        private const val STATUS_ERROR = 400
        private const val STATUS_BAD_CONFIGURATION = 401
        private const val STATUS_BAD_SECRET = 402

        private const val ENCODING_HEXADECIMAL = 3
        private const val ROLE_INPUT = 1

        private fun statusText(status: Int) = when (status) {
            STATUS_ERROR -> "genel hata"
            STATUS_BAD_CONFIGURATION -> "yapılandırma reddedildi"
            STATUS_BAD_SECRET -> "kod yanlış"
            else -> "bilinmiyor ($status)"
        }

        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Onaltilik metin cift uzunlukta olmali" }
            return ByteArray(hex.length / 2) {
                hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            }
        }
    }
}

class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause)
