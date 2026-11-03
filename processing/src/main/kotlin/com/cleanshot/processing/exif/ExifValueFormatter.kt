package com.cleanshot.processing.exif

import com.cleanshot.processing.util.u16be
import com.cleanshot.processing.util.u16le
import com.cleanshot.processing.util.u32be
import com.cleanshot.processing.util.u32le
import java.util.Locale
import kotlin.math.abs

/** Converts raw TIFF values to human readable strings. */
internal class ExifValueFormatter(private val le: Boolean) {

    fun u16(b: ByteArray, i: Int) = if (le) b.u16le(i) else b.u16be(i)
    fun u32(b: ByteArray, i: Int) = if (le) b.u32le(i) else b.u32be(i)

    fun numbers(e: TiffEntry): List<Double> {
        val n = e.count.toInt().coerceAtMost(64)
        val v = e.value
        return when (e.type) {
            1, 7 -> (0 until minOf(n, v.size)).map { (v[it].toInt() and 0xFF).toDouble() }
            6 -> (0 until minOf(n, v.size)).map { v[it].toDouble() }
            3 -> (0 until n).filter { it * 2 + 2 <= v.size }.map { u16(v, it * 2).toDouble() }
            8 -> (0 until n).filter { it * 2 + 2 <= v.size }.map { u16(v, it * 2).toShort().toDouble() }
            4 -> (0 until n).filter { it * 4 + 4 <= v.size }.map { u32(v, it * 4).toDouble() }
            9 -> (0 until n).filter { it * 4 + 4 <= v.size }.map { u32(v, it * 4).toInt().toDouble() }
            5 -> (0 until n).filter { it * 8 + 8 <= v.size }.map {
                val d = u32(v, it * 8 + 4); if (d == 0L) 0.0 else u32(v, it * 8).toDouble() / d
            }
            10 -> (0 until n).filter { it * 8 + 8 <= v.size }.map {
                val d = u32(v, it * 8 + 4).toInt(); if (d == 0) 0.0 else u32(v, it * 8).toInt().toDouble() / d
            }
            11 -> (0 until n).filter { it * 4 + 4 <= v.size }.map { java.lang.Float.intBitsToFloat(u32(v, it * 4).toInt()).toDouble() }
            12 -> (0 until n).filter { it * 8 + 8 <= v.size }.map {
                val hi = u32(v, it * 8); val lo = u32(v, it * 8 + 4)
                java.lang.Double.longBitsToDouble(if (le) (lo shl 32) or hi else (hi shl 32) or lo)
            }
            else -> emptyList()
        }
    }

    fun ascii(e: TiffEntry): String = String(e.value, Charsets.UTF_8).trimEnd('\u0000').trim()

    fun format(ifd: IfdType, e: TiffEntry): String {
        val tag = e.tag
        if (ifd == IfdType.IFD0 && tag in 0x9C9B..0x9C9F) {
            return String(e.value, Charsets.UTF_16LE).trimEnd('\u0000').trim()
        }
        if (tag == TiffTags.MAKER_NOTE) return "${e.value.size} bytes de dados proprietários"
        if (tag == 0x8773) return "${e.value.size} bytes"
        if (tag == 0x9286) return userComment(e.value)
        if (ifd == IfdType.GPS) formatGps(tag, e)?.let { return it }
        val nums by lazy { numbers(e) }
        return when (tag) {
            0x0112 -> ORIENTATION[nums.firstOrNull()?.toInt()] ?: nums.firstOrNull()?.toInt().toString()
            0x829A -> nums.firstOrNull()?.let { if (it in 0.0..0.5 && it > 0) "1/${Math.round(1 / it)} s" else fmt(it) + " s" } ?: "?"
            0x829D -> nums.firstOrNull()?.let { "f/" + fmt(it, 1) } ?: "?"
            0x920A -> nums.firstOrNull()?.let { fmt(it, 1) + " mm" } ?: "?"
            0xA405 -> nums.firstOrNull()?.let { "${it.toInt()} mm" } ?: "?"
            0x9209 -> nums.firstOrNull()?.toInt()?.let { if (it and 1 == 1) "Disparado" else "Não disparado" } ?: "?"
            0xA001 -> when (nums.firstOrNull()?.toInt()) { 1 -> "sRGB"; 0xFFFF -> "Não calibrado"; else -> nums.toString() }
            0x9000, 0xA000 -> String(e.value, Charsets.ISO_8859_1)
            else -> generic(e)
        }
    }

    fun generic(e: TiffEntry): String = when (e.type) {
        2 -> ascii(e)
        7 -> {
            val printable = e.value.all { it in 0x20..0x7E || it == 0.toByte() }
            if (printable && e.value.isNotEmpty()) String(e.value, Charsets.ISO_8859_1).trimEnd('\u0000')
            else "${e.value.size} bytes"
        }
        else -> numbers(e).joinToString(", ") { fmt(it) }.let { if (e.count > 64) "$it…" else it }
    }

    private fun formatGps(tag: Int, e: TiffEntry): String? = when (tag) {
        0x02, 0x04 -> numbers(e).takeIf { it.size >= 3 }?.let { "%d° %d' %s\"".format(it[0].toInt(), it[1].toInt(), fmt(it[2], 2)) }
        0x06 -> numbers(e).firstOrNull()?.let { fmt(it, 1) + " m" }
        0x07 -> numbers(e).takeIf { it.size >= 3 }?.let { "%02d:%02d:%02d".format(it[0].toInt(), it[1].toInt(), it[2].toInt()) }
        0x1B, 0x1C -> userComment(e.value)
        else -> null
    }

    companion object {
        val ORIENTATION = mapOf(
            1 to "Normal", 2 to "Espelhada horizontalmente", 3 to "Girada 180°", 4 to "Espelhada verticalmente",
            5 to "Espelhada e girada 90° anti-horário", 6 to "Girada 90° horário", 7 to "Espelhada e girada 90° horário",
            8 to "Girada 90° anti-horário",
        )

        fun fmt(v: Double, decimals: Int = 4): String {
            if (v == Math.floor(v) && abs(v) < 1e12) return v.toLong().toString()
            return String.format(Locale.US, "%.${decimals}f", v).trimEnd('0').trimEnd('.')
        }

        fun userComment(v: ByteArray): String {
            if (v.size < 8) return String(v, Charsets.ISO_8859_1).trimEnd('\u0000').trim()
            val prefix = String(v, 0, 8, Charsets.ISO_8859_1)
            val body = v.copyOfRange(8, v.size)
            return when {
                prefix.startsWith("ASCII") -> String(body, Charsets.ISO_8859_1)
                prefix.startsWith("UNICODE") -> decodeUnicode(body)
                prefix.startsWith("JIS") -> String(body, charset("Shift_JIS"))
                prefix.all { it == '\u0000' || it == ' ' } -> String(body, Charsets.UTF_8)
                else -> String(v, Charsets.UTF_8)
            }.trimEnd('\u0000', ' ').trim()
        }

        private fun decodeUnicode(b: ByteArray): String {
            // EXIF does not define the byte order of UNICODE comments; guess from zero bytes.
            val evenZeros = b.indices.step(2).count { b[it] == 0.toByte() }
            val oddZeros = (1 until b.size step 2).count { b[it] == 0.toByte() }
            return String(b, if (evenZeros > oddZeros) Charsets.UTF_16BE else Charsets.UTF_16LE)
        }
    }
}
