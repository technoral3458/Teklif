package com.technoral.tvkumanda.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Cok kucuk bir DER (ASN.1) yazicisi.
 *
 * Android'in standart kutuphanesinde sertifika *uretme* API'si yok; bunun icin
 * genelde BouncyCastle eklenir. Ihtiyacimiz tek bir self-signed RSA sertifikasi
 * oldugu icin ~5 MB'lik bagimlilik yerine gereken DER yapisi elle yaziliyor.
 */
internal object Der {

    fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val len = content.size
        if (len < 0x80) {
            out.write(len)
        } else {
            var tmp = len
            val lenBytes = ArrayDeque<Int>()
            while (tmp > 0) {
                lenBytes.addFirst(tmp and 0xFF)
                tmp = tmp ushr 8
            }
            out.write(0x80 or lenBytes.size)
            lenBytes.forEach { out.write(it) }
        }
        out.write(content)
        return out.toByteArray()
    }

    fun sequence(vararg parts: ByteArray) = tlv(0x30, concat(*parts))

    fun set(vararg parts: ByteArray) = tlv(0x31, concat(*parts))

    fun integer(value: BigInteger) = tlv(0x02, value.toByteArray())

    fun oid(encoded: ByteArray) = tlv(0x06, encoded)

    fun nullValue() = byteArrayOf(0x05, 0x00)

    fun bitString(content: ByteArray) = tlv(0x03, concat(byteArrayOf(0), content))

    fun utf8String(value: String) = tlv(0x0C, value.toByteArray(Charsets.UTF_8))

    fun utcTime(value: String) = tlv(0x17, value.toByteArray(Charsets.US_ASCII))

    /** Baglama ozgu (context-specific) EXPLICIT etiket, ornek: [0] icin n = 0. */
    fun explicit(n: Int, content: ByteArray) = tlv(0xA0 or n, content)

    fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** 1.2.840.113549.1.1.11 - sha256WithRSAEncryption */
    val OID_SHA256_WITH_RSA = byteArrayOf(
        0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B,
    )

    /** 2.5.4.3 - commonName */
    val OID_COMMON_NAME = byteArrayOf(0x55, 0x04, 0x03)
}

/**
 * Telefonun kimligi. Eslesme sirasinda TV bu sertifikayi kaydeder; sonraki
 * baglantilarda ayni sertifika sunulmazsa TV baglantiyi reddeder.
 *
 * Bu yuzden anahtar bir kez uretilip uygulamanin ozel dizinindeki PKCS#12
 * deposunda saklanir - silinirse TV ile yeniden eslesmek gerekir.
 */
class ClientCertificate(
    val certificate: X509Certificate,
    val privateKey: PrivateKey,
) {

    /** SHA-256 ozeti icin gereken RSA modulus'unun isaretsiz big-endian gosterimi. */
    fun modulusBytes(): ByteArray = unsignedBytes((certificate.publicKey as RSAPublicKey).modulus)

    fun exponentBytes(): ByteArray = unsignedBytes((certificate.publicKey as RSAPublicKey).publicExponent)

    companion object {
        private const val ALIAS = "tvkumanda-client"
        private val PASSWORD = "tvkumanda".toCharArray()

        /**
         * [file] icindeki PKCS#12 deposunu yukler; yoksa yeni bir anahtar cifti
         * ve self-signed sertifika uretip kaydeder.
         */
        fun loadOrCreate(file: File, commonName: String = "tvkumanda"): ClientCertificate {
            if (file.exists()) {
                runCatching { load(file) }.getOrNull()?.let { return it }
            }
            val created = generate(commonName)
            save(created, file)
            return created
        }

        fun load(file: File): ClientCertificate {
            val store = KeyStore.getInstance("PKCS12")
            file.inputStream().use { store.load(it, PASSWORD) }
            val key = store.getKey(ALIAS, PASSWORD) as PrivateKey
            val cert = store.getCertificate(ALIAS) as X509Certificate
            return ClientCertificate(cert, key)
        }

        fun save(client: ClientCertificate, file: File) {
            val store = KeyStore.getInstance("PKCS12")
            store.load(null, PASSWORD)
            store.setKeyEntry(ALIAS, client.privateKey, PASSWORD, arrayOf(client.certificate))
            file.parentFile?.mkdirs()
            file.outputStream().use { store.store(it, PASSWORD) }
        }

        /** RSA-2048 anahtar cifti + self-signed X.509 v3 sertifikasi uretir. */
        fun generate(commonName: String): ClientCertificate {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            val keyPair = generator.generateKeyPair()

            val sigAlgorithm = Der.sequence(Der.oid(Der.OID_SHA256_WITH_RSA), Der.nullValue())
            val name = Der.sequence(
                Der.set(Der.sequence(Der.oid(Der.OID_COMMON_NAME), Der.utf8String(commonName))),
            )

            val format = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val notBefore = Der.utcTime(format.format(Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000)))
            // UTCTime yalnizca 2049'a kadar gecerli; sertifikanin pratikte suresiz
            // olmasi icin ust siniri kullaniyoruz.
            val notAfter = Der.utcTime("491231235959Z")

            val serial = BigInteger(64, SecureRandom()).add(BigInteger.ONE)

            val tbs = Der.sequence(
                Der.explicit(0, Der.integer(BigInteger.valueOf(2))), // v3
                Der.integer(serial),
                sigAlgorithm,
                name,                              // issuer
                Der.sequence(notBefore, notAfter), // validity
                name,                              // subject
                keyPair.public.encoded,            // SubjectPublicKeyInfo (zaten DER)
            )

            val signature = Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(tbs)
                sign()
            }

            val der = Der.sequence(tbs, sigAlgorithm, Der.bitString(signature))
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate

            return ClientCertificate(certificate, keyPair.private)
        }

        /** BigInteger'in isaret baytini atarak isaretsiz big-endian gosterimini verir. */
        fun unsignedBytes(value: BigInteger): ByteArray {
            val raw = value.toByteArray()
            var start = 0
            while (start < raw.size - 1 && raw[start] == 0.toByte()) start++
            return if (start == 0) raw else raw.copyOfRange(start, raw.size)
        }
    }
}
