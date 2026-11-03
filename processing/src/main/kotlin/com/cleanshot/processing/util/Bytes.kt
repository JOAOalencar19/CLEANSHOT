package com.cleanshot.processing.util

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

internal fun ByteArray.u16be(i: Int): Int = (u8(i) shl 8) or u8(i + 1)
internal fun ByteArray.u16le(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
internal fun ByteArray.u32be(i: Int): Long =
    ((u8(i).toLong() shl 24) or (u8(i + 1).toLong() shl 16) or (u8(i + 2).toLong() shl 8) or u8(i + 3).toLong())
internal fun ByteArray.u32le(i: Int): Long =
    (u8(i).toLong() or (u8(i + 1).toLong() shl 8) or (u8(i + 2).toLong() shl 16) or (u8(i + 3).toLong() shl 24))
internal fun ByteArray.u64be(i: Int): Long = (u32be(i) shl 32) or u32be(i + 4)

internal fun ByteArray.ascii(start: Int, len: Int): String =
    String(this, start, len.coerceAtMost(size - start).coerceAtLeast(0), Charsets.ISO_8859_1)

internal fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean {
    if (at < 0 || at + prefix.size > size) return false
    for (k in prefix.indices) if (this[at + k] != prefix[k]) return false
    return true
}

internal fun ByteArray.indexOf(needle: ByteArray, from: Int = 0): Int {
    if (needle.isEmpty()) return from
    outer@ for (i in from..size - needle.size) {
        for (k in needle.indices) if (this[i + k] != needle[k]) continue@outer
        return i
    }
    return -1
}

internal fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()
internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Growable byte buffer with endian-aware writers. */
internal class ByteSink(initial: Int = 1024) {
    private val out = ByteArrayOutputStream(initial)
    val size: Int get() = out.size()

    fun write(b: ByteArray) { out.write(b) }
    fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
    fun u8(v: Int) { out.write(v and 0xFF) }
    fun u16be(v: Int) { u8(v shr 8); u8(v) }
    fun u32be(v: Long) { u8((v shr 24).toInt()); u8((v shr 16).toInt()); u8((v shr 8).toInt()); u8(v.toInt()) }
    fun u32le(v: Long) { u8(v.toInt()); u8((v shr 8).toInt()); u8((v shr 16).toInt()); u8((v shr 24).toInt()) }
    fun ascii(s: String) { out.write(s.toByteArray(Charsets.ISO_8859_1)) }
    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Raised by container parsers when a structure is truncated or inconsistent. */
class MalformedImageException(message: String) : Exception(message)

internal fun require(condition: Boolean, message: () -> String) {
    if (!condition) throw MalformedImageException(message())
}
