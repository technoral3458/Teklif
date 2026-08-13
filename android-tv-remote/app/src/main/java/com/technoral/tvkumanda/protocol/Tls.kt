package com.technoral.tvkumanda.protocol

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * TV kendi urettigi self-signed sertifikayi sunar; guvenilir bir kok sertifika
 * zinciri yoktur. Bu yuzden zincir dogrulamasi yapilamaz.
 *
 * Bunun yerine: ilk eslesmede TV'nin sertifika parmak izi kaydedilir, sonraki
 * baglantilarda ayni parmak izi beklenir. Boylece ayni ag icinde baska bir
 * cihazin TV taklidi yapmasi engellenir.
 */
class PinningTrustManager(
    private val expectedFingerprint: String?,
) : X509TrustManager {

    /** El sikismada gorulen sunucu sertifikasi - eslesme sonrasi kaydedilir. */
    @Volatile
    var seenFingerprint: String? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("Sunucu sertifikası yok")
        val fingerprint = fingerprintOf(leaf)
        seenFingerprint = fingerprint
        val expected = expectedFingerprint ?: return // ilk eslesme: guvenip kaydet
        if (!expected.equals(fingerprint, ignoreCase = true)) {
            throw java.security.cert.CertificateException(
                "TV sertifikası değişti. Beklenen $expected, gelen $fingerprint",
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun fingerprintOf(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString(":") { "%02X".format(it) }
    }
}

object Tls {

    /**
     * Istemci sertifikasini sunan bir [SSLContext] uretir. TV, eslesmis
     * telefonu bu sertifikadan tanir.
     */
    fun contextFor(
        client: ClientCertificate,
        trustManager: PinningTrustManager,
    ): SSLContext {
        val password = "tvkumanda".toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry("client", client.privateKey, password, arrayOf(client.certificate))
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)

        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, arrayOf<javax.net.ssl.TrustManager>(trustManager), null)
        }
    }

    /** Bazi TV yazilimlari yalnizca TLS 1.2 konusur; ikisini de aciyoruz. */
    fun configure(socket: SSLSocket) {
        val wanted = listOf("TLSv1.2", "TLSv1.3")
        val supported = socket.supportedProtocols.toSet()
        val enabled = wanted.filter { it in supported }
        if (enabled.isNotEmpty()) socket.enabledProtocols = enabled.toTypedArray()
        socket.soTimeout = 0
        socket.tcpNoDelay = true
    }
}
