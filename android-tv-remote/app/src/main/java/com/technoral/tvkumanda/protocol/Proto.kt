package com.technoral.tvkumanda.protocol

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Elle yazilmis, minimal protobuf (proto3) kodlayici/cozucu.
 *
 * Android TV Remote v2 protokolu yalnizca varint, uzunluk-onekli alan ve ic mesaj
 * kullanir; bu yuzden protoc/codegen bagimliligi eklemek yerine ihtiyac duyulan
 * kadari burada uygulanmistir.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) = apply {
        writeTag(field, WIRE_VARINT)
        writeVarint(value)
    }

    fun int32(field: Int, value: Int) = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean) = varint(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray) = apply {
        writeTag(field, WIRE_LENGTH)
        writeVarint(value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    /** Ic mesaj yazar: `message(10) { string(1, "abc") }` */
    fun message(field: Int, block: ProtoWriter.() -> Unit) =
        bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun writeTag(field: Int, wire: Int) =
        writeVarint((field.toLong() shl 3) or wire.toLong())

    private fun writeVarint(value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) {
                out.write(b)
                return
            }
            out.write(b or 0x80)
        }
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_64BIT = 1
        const val WIRE_LENGTH = 2
        const val WIRE_32BIT = 5

        fun encodeVarint(value: Long): ByteArray = ProtoWriter().also { it.writeVarint(value) }.toByteArray()
    }
}

/**
 * Cozulmus bir protobuf mesaji. Alanlar numaraya gore saklanir; varint alanlar
 * [Long], uzunluk-onekli alanlar [ByteArray] olarak tutulur.
 */
class ProtoMessage(private val fields: Map<Int, List<Any>>) {

    fun has(field: Int): Boolean = fields.containsKey(field)

    fun varint(field: Int): Long? = fields[field]?.firstOrNull() as? Long

    fun int(field: Int): Int? = varint(field)?.toInt()

    fun bool(field: Int): Boolean? = varint(field)?.let { it != 0L }

    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray

    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    /** Mesajda dolu olan alan numaralari - hangi "oneof" dalinin geldigini anlamak icin. */
    fun fieldNumbers(): Set<Int> = fields.keys

    override fun toString(): String = "ProtoMessage(fields=${fields.keys.sorted()})"

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val map = LinkedHashMap<Int, MutableList<Any>>()
            var pos = 0

            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size) throw EOFException("protobuf: eksik varint")
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IllegalStateException("protobuf: bozuk varint")
                }
            }

            while (pos < data.size) {
                val tag = readVarint().toInt()
                val field = tag ushr 3
                when (tag and 7) {
                    ProtoWriter.WIRE_VARINT -> map.getOrPut(field) { mutableListOf() }.add(readVarint())
                    ProtoWriter.WIRE_64BIT -> pos += 8
                    ProtoWriter.WIRE_LENGTH -> {
                        val len = readVarint().toInt()
                        if (len < 0 || pos + len > data.size) throw EOFException("protobuf: eksik veri")
                        map.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(pos, pos + len))
                        pos += len
                    }
                    ProtoWriter.WIRE_32BIT -> pos += 4
                    else -> throw IllegalStateException("protobuf: bilinmeyen wire type")
                }
            }
            return ProtoMessage(map)
        }
    }
}

/**
 * Her iki kanal da (eslesme 6467 / kumanda 6466) mesajlari varint uzunluk oneki
 * ile cerceveler.
 */
object MessageFraming {

    fun write(out: OutputStream, payload: ByteArray) {
        out.write(ProtoWriter.encodeVarint(payload.size.toLong()))
        out.write(payload)
        out.flush()
    }

    /** Bloklayarak tek bir mesaj okur. Akis kapandiysa null doner. */
    fun read(input: InputStream): ByteArray? {
        var length = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            length = length or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 35) throw IllegalStateException("cerceve: bozuk uzunluk oneki")
        }
        if (length < 0 || length > MAX_MESSAGE_BYTES) {
            throw IllegalStateException("cerceve: makul olmayan mesaj boyutu ($length)")
        }
        val payload = ByteArray(length.toInt())
        var read = 0
        while (read < payload.size) {
            val n = input.read(payload, read, payload.size - read)
            if (n < 0) return null
            read += n
        }
        return payload
    }

    private const val MAX_MESSAGE_BYTES = 1 shl 20
}
