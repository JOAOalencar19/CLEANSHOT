package com.cleanshot.processing.container

import com.cleanshot.processing.util.ByteSink
import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.indexOf
import com.cleanshot.processing.util.u32be
import com.cleanshot.processing.util.u8
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Inflater

/** PNG chunk list. Chunks that are not rewritten keep their original bytes and CRC. */
class PngFile(val chunks: List<Chunk>) {

    class Chunk(val type: String, val data: ByteArray, val crc: Long = crcOf(type, data)) {
        val isCritical: Boolean get() = type[0].isUpperCase()
        /** Chunks that carry or define pixel data (never modified). */
        val isImageData: Boolean get() = type in IMAGE_CHUNKS
    }

    data class TextChunk(val keyword: String, val text: String, val kind: String)

    fun write(newChunks: List<Chunk> = chunks): ByteArray {
        val s = ByteSink(newChunks.sumOf { it.data.size + 12 } + 8)
        s.write(SIGNATURE)
        for (c in newChunks) {
            s.u32be(c.data.size.toLong()); s.ascii(c.type); s.write(c.data); s.u32be(c.crc)
        }
        return s.toByteArray()
    }

    fun ihdr(): Chunk? = chunks.firstOrNull { it.type == "IHDR" }
    fun dimensions(): Pair<Int, Int>? = ihdr()?.data?.let { it.u32be(0).toInt() to it.u32be(4).toInt() }
    fun bitDepth(): Int? = ihdr()?.data?.u8(8)
    fun colorType(): Int? = ihdr()?.data?.u8(9)
    fun hasAlpha(): Boolean = colorType().let { it == 4 || it == 6 } || chunks.any { it.type == "tRNS" }
    fun isAnimated(): Boolean = chunks.any { it.type == "acTL" }

    companion object {
        val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val IMAGE_CHUNKS = setOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "acTL", "fcTL", "fdAT", "sBIT", "bKGD", "pHYs", "sPLT", "hIST")
        val TEXT_CHUNKS = setOf("tEXt", "zTXt", "iTXt")
        /** Colour management chunks, always preserved. */
        val COLOR_CHUNKS = setOf("iCCP", "sRGB", "gAMA", "cHRM", "cICP", "mDCV", "cLLI")

        fun crcOf(type: String, data: ByteArray): Long = CRC32().apply {
            update(type.toByteArray(Charsets.ISO_8859_1)); update(data)
        }.value

        fun parse(data: ByteArray): PngFile {
            if (data.size < 8 || !SIGNATURE.indices.all { data[it] == SIGNATURE[it] }) throw MalformedImageException("Não é um PNG válido")
            val chunks = mutableListOf<Chunk>()
            var p = 8
            while (p + 12 <= data.size) {
                val len = data.u32be(p)
                if (len > Int.MAX_VALUE || p + 12 + len > data.size) throw MalformedImageException("Chunk PNG truncado")
                val type = String(data, p + 4, 4, Charsets.ISO_8859_1)
                if (!type.all { it.isLetter() }) throw MalformedImageException("Tipo de chunk PNG inválido")
                val body = data.copyOfRange(p + 8, p + 8 + len.toInt())
                val crc = data.u32be(p + 8 + len.toInt())
                if (crc != crcOf(type, body)) throw MalformedImageException("CRC inválido no chunk $type (arquivo corrompido)")
                chunks += Chunk(type, body, crc)
                p += 12 + len.toInt()
                if (type == "IEND") break
            }
            if (chunks.firstOrNull()?.type != "IHDR") throw MalformedImageException("PNG sem IHDR")
            if (chunks.none { it.type == "IDAT" }) throw MalformedImageException("PNG sem dados de imagem (IDAT)")
            if (chunks.last().type != "IEND") throw MalformedImageException("PNG truncado (sem IEND)")
            return PngFile(chunks)
        }

        fun decodeText(c: Chunk): TextChunk? = runCatching {
            val d = c.data
            val nul = d.indexOf(byteArrayOf(0))
            if (nul <= 0) return null
            val keyword = String(d, 0, nul, Charsets.ISO_8859_1)
            when (c.type) {
                "tEXt" -> TextChunk(keyword, String(d, nul + 1, d.size - nul - 1, Charsets.ISO_8859_1), c.type)
                "zTXt" -> TextChunk(keyword, String(inflate(d.copyOfRange(nul + 2, d.size)), Charsets.ISO_8859_1), c.type)
                "iTXt" -> {
                    val compressed = d[nul + 1].toInt() == 1
                    var q = nul + 3
                    val langEnd = d.indexOf(byteArrayOf(0), q); q = langEnd + 1
                    val tkEnd = d.indexOf(byteArrayOf(0), q); q = tkEnd + 1
                    val raw = d.copyOfRange(q, d.size)
                    TextChunk(keyword, String(if (compressed) inflate(raw) else raw, Charsets.UTF_8), c.type)
                }
                else -> null
            }
        }.getOrNull()

        /** Builds an iTXt chunk (uncompressed) with UTF-8 text. */
        fun iTxt(keyword: String, text: ByteArray): Chunk {
            val s = ByteSink()
            s.ascii(keyword); s.u8(0); s.u8(0); s.u8(0); s.u8(0); s.u8(0); s.write(text)
            return Chunk("iTXt", s.toByteArray())
        }

        private fun inflate(b: ByteArray): ByteArray {
            val inf = Inflater()
            inf.setInput(b)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var guard = 0
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > 32 * 1024 * 1024 || ++guard > 100_000) throw MalformedImageException("Texto PNG comprimido grande demais")
            }
            inf.end()
            return out.toByteArray()
        }
    }
}
