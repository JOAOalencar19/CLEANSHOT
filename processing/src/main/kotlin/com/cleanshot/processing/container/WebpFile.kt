package com.cleanshot.processing.container

import com.cleanshot.processing.util.ByteSink
import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.u16le
import com.cleanshot.processing.util.u32le
import com.cleanshot.processing.util.u8

/** WebP RIFF chunk list. */
class WebpFile(val chunks: List<Chunk>) {

    class Chunk(val fourcc: String, val data: ByteArray)

    val isExtended: Boolean get() = chunks.firstOrNull()?.fourcc == "VP8X"
    val isAnimated: Boolean get() = chunks.any { it.fourcc == "ANIM" || it.fourcc == "ANMF" }

    fun write(newChunks: List<Chunk> = chunks): ByteArray {
        val body = ByteSink()
        body.ascii("WEBP")
        for (c in newChunks) {
            body.ascii(c.fourcc); body.u32le(c.data.size.toLong()); body.write(c.data)
            if (c.data.size % 2 == 1) body.u8(0)
        }
        val b = body.toByteArray()
        val s = ByteSink(b.size + 8)
        s.ascii("RIFF"); s.u32le(b.size.toLong()); s.write(b)
        return s.toByteArray()
    }

    fun dimensions(): Pair<Int, Int>? {
        val first = chunks.firstOrNull() ?: return null
        val d = first.data
        return when (first.fourcc) {
            "VP8X" -> if (d.size >= 10) (1 + (d.u8(4) or (d.u8(5) shl 8) or (d.u8(6) shl 16))) to (1 + (d.u8(7) or (d.u8(8) shl 8) or (d.u8(9) shl 16))) else null
            "VP8 " -> if (d.size >= 10) (d.u16le(6) and 0x3FFF) to (d.u16le(8) and 0x3FFF) else null
            "VP8L" -> if (d.size >= 5) {
                val bits = d.u32le(1)
                ((bits and 0x3FFF) + 1).toInt() to (((bits shr 14) and 0x3FFF) + 1).toInt()
            } else null
            else -> null
        }
    }

    fun hasAlpha(): Boolean = when {
        isExtended -> (chunks.first().data.u8(0) and FLAG_ALPHA) != 0
        chunks.firstOrNull()?.fourcc == "VP8L" -> chunks.first().data.let { it.size >= 5 && (it.u8(4) and 0x10) != 0 }
        else -> false
    }

    companion object {
        const val FLAG_ICC = 0x20
        const val FLAG_ALPHA = 0x10
        const val FLAG_EXIF = 0x08
        const val FLAG_XMP = 0x04
        const val FLAG_ANIMATION = 0x02
        val IMAGE_CHUNKS = setOf("VP8 ", "VP8L", "ALPH", "ANIM", "ANMF")

        fun parse(data: ByteArray): WebpFile {
            if (data.size < 20 || String(data, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(data, 8, 4, Charsets.ISO_8859_1) != "WEBP") {
                throw MalformedImageException("Não é um WebP válido")
            }
            val riffSize = data.u32le(4)
            if (riffSize + 8 > data.size) throw MalformedImageException("WebP truncado")
            val end = (riffSize + 8).toInt()
            val chunks = mutableListOf<Chunk>()
            var p = 12
            while (p + 8 <= end) {
                val fourcc = String(data, p, 4, Charsets.ISO_8859_1)
                val len = data.u32le(p + 4)
                if (p + 8 + len > end) throw MalformedImageException("Chunk WebP truncado ($fourcc)")
                chunks += Chunk(fourcc, data.copyOfRange(p + 8, p + 8 + len.toInt()))
                p += 8 + len.toInt() + (len.toInt() and 1)
            }
            if (chunks.none { it.fourcc in IMAGE_CHUNKS }) throw MalformedImageException("WebP sem dados de imagem")
            return WebpFile(chunks)
        }
    }
}
