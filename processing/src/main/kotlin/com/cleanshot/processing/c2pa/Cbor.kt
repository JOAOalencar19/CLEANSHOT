package com.cleanshot.processing.c2pa

import com.cleanshot.processing.util.MalformedImageException
import java.io.ByteArrayOutputStream
import java.math.BigInteger

/** Tagged CBOR value. */
data class CborTag(val tag: Long, val value: Any?)

/** Marker for the CBOR "undefined" simple value. */
object CborUndefined

/**
 * Small RFC 8949 CBOR decoder (read-only). Produces Long/BigInteger, ByteArray, String,
 * List, LinkedHashMap, Boolean, Double, null, [CborTag] and [CborUndefined].
 */
class CborReader(private val b: ByteArray, private var p: Int = 0, private val end: Int = b.size) {

    val position: Int get() = p

    fun read(depth: Int = 0): Any? {
        if (depth > 64) throw MalformedImageException("CBOR aninhado demais")
        val ib = byte()
        val major = ib shr 5
        val info = ib and 0x1F
        return when (major) {
            0 -> arg(info)
            1 -> when (val a = arg(info)) {
                is Long -> -1 - a
                is BigInteger -> BigInteger.ONE.negate().subtract(a)
                else -> a
            }
            2 -> if (info == 31) chunks(2) else bytes(len(info))
            3 -> if (info == 31) String(chunks(3), Charsets.UTF_8) else String(bytes(len(info)), Charsets.UTF_8)
            4 -> {
                val list = ArrayList<Any?>()
                if (info == 31) { while (peek() != 0xFF) list += read(depth + 1); p++ }
                else repeat(len(info)) { list += read(depth + 1) }
                list
            }
            5 -> {
                val map = LinkedHashMap<Any?, Any?>()
                if (info == 31) { while (peek() != 0xFF) { val k = read(depth + 1); map[k] = read(depth + 1) }; p++ }
                else repeat(len(info)) { val k = read(depth + 1); map[k] = read(depth + 1) }
                map
            }
            6 -> CborTag(arg(info).let { (it as? Long) ?: -1L }, read(depth + 1))
            else -> when (info) {
                20 -> false
                21 -> true
                22 -> null
                23 -> CborUndefined
                25 -> half(u(2).toInt())
                26 -> java.lang.Float.intBitsToFloat(u(4).toInt()).toDouble()
                27 -> java.lang.Double.longBitsToDouble(u(8))
                in 0..19 -> info.toLong()
                24 -> u(1)
                else -> throw MalformedImageException("CBOR: valor simples inválido")
            }
        }
    }

    private fun byte(): Int {
        if (p >= end) throw MalformedImageException("CBOR truncado")
        return b[p++].toInt() and 0xFF
    }

    private fun peek(): Int { if (p >= end) throw MalformedImageException("CBOR truncado"); return b[p].toInt() and 0xFF }

    private fun u(n: Int): Long { var v = 0L; repeat(n) { v = (v shl 8) or byte().toLong() }; return v }

    private fun arg(info: Int): Any = when {
        info < 24 -> info.toLong()
        info == 24 -> u(1)
        info == 25 -> u(2)
        info == 26 -> u(4)
        info == 27 -> {
            val hi = u(4); val lo = u(4)
            if (hi and 0x80000000L != 0L) BigInteger.valueOf(hi).shiftLeft(32).or(BigInteger.valueOf(lo)) else (hi shl 32) or lo
        }
        else -> throw MalformedImageException("CBOR: argumento inválido")
    }

    private fun len(info: Int): Int {
        val a = arg(info)
        val l = (a as? Long) ?: throw MalformedImageException("CBOR: tamanho grande demais")
        if (l < 0 || l > end - p) throw MalformedImageException("CBOR: tamanho inválido")
        return l.toInt()
    }

    private fun bytes(n: Int): ByteArray { val r = b.copyOfRange(p, p + n); p += n; return r }

    private fun chunks(major: Int): ByteArray {
        val out = ByteArrayOutputStream()
        while (peek() != 0xFF) {
            val ib = byte()
            if (ib shr 5 != major) throw MalformedImageException("CBOR: fragmento inválido")
            out.write(bytes(len(ib and 0x1F)))
        }
        p++
        return out.toByteArray()
    }

    private fun half(h: Int): Double {
        val exp = (h shr 10) and 0x1F
        val mant = h and 0x3FF
        val v = when (exp) {
            0 -> mant * Math.pow(2.0, -24.0)
            31 -> if (mant == 0) Double.POSITIVE_INFINITY else Double.NaN
            else -> (mant + 1024) * Math.pow(2.0, exp - 25.0)
        }
        return if (h and 0x8000 != 0) -v else v
    }

    companion object {
        fun decode(bytes: ByteArray): Any? = CborReader(bytes).read()
    }
}

/** Minimal CBOR encoder used to build the COSE Sig_structure for signature verification. */
internal object CborWriter {
    fun head(major: Int, value: Long, out: ByteArrayOutputStream) {
        val m = major shl 5
        when {
            value < 24 -> out.write(m or value.toInt())
            value < 0x100 -> { out.write(m or 24); out.write(value.toInt()) }
            value < 0x10000 -> { out.write(m or 25); out.write((value shr 8).toInt()); out.write(value.toInt() and 0xFF) }
            value < 0x100000000L -> { out.write(m or 26); for (s in 24 downTo 0 step 8) out.write((value shr s).toInt() and 0xFF) }
            else -> { out.write(m or 27); for (s in 56 downTo 0 step 8) out.write((value shr s).toInt() and 0xFF) }
        }
    }

    fun sigStructure(protected: ByteArray, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        head(4, 4, out)
        val ctx = "Signature1".toByteArray()
        head(3, ctx.size.toLong(), out); out.write(ctx)
        head(2, protected.size.toLong(), out); out.write(protected)
        head(2, 0, out)
        head(2, payload.size.toLong(), out); out.write(payload)
        return out.toByteArray()
    }
}

/** Convenience accessors for decoded CBOR maps. */
internal fun Any?.cborMap(): Map<Any?, Any?>? = when (this) {
    is Map<*, *> -> @Suppress("UNCHECKED_CAST") (this as Map<Any?, Any?>)
    is CborTag -> value.cborMap()
    else -> null
}

internal fun Any?.cborList(): List<Any?>? = when (this) {
    is List<*> -> this
    is CborTag -> value.cborList()
    else -> null
}

internal fun Any?.cborString(): String? = when (this) {
    is String -> this
    is CborTag -> value.cborString()
    else -> null
}

internal fun Any?.cborLong(): Long? = when (this) {
    is Long -> this
    is BigInteger -> this.toLong()
    is CborTag -> value.cborLong()
    else -> null
}
