package com.cleanshot.processing.container

import com.cleanshot.processing.util.ByteSink
import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.startsWith
import com.cleanshot.processing.util.u16be
import com.cleanshot.processing.util.u8

/**
 * JPEG split into header segments and the untouched image data.
 * Everything from the first SOS marker to the end of the file (entropy-coded data, later scans,
 * EOI and any trailer such as gain maps / Motion Photo) is kept byte for byte.
 */
class JpegFile(val segments: List<Segment>, val imageData: ByteArray) {

    class Segment(val marker: Int, val payload: ByteArray) {
        val isApp: Boolean get() = marker in 0xE0..0xEF
        fun isExif() = marker == 0xE1 && payload.startsWith(EXIF_HEADER)
        fun isXmp() = marker == 0xE1 && payload.startsWith(XMP_HEADER)
        fun isExtendedXmp() = marker == 0xE1 && payload.startsWith(XMP_EXT_HEADER)
        fun isIcc() = marker == 0xE2 && payload.startsWith(ICC_HEADER)
        fun isMpf() = marker == 0xE2 && payload.startsWith(MPF_HEADER)
        fun isPhotoshop() = marker == 0xED && payload.startsWith(PHOTOSHOP_HEADER)
        fun isJumbf() = marker == 0xEB && payload.size >= 2 && payload[0] == 'J'.code.toByte() && payload[1] == 'P'.code.toByte()
        fun isComment() = marker == 0xFE
        fun isFrame() = marker in SOF_MARKERS
    }

    fun write(newSegments: List<Segment> = segments): ByteArray {
        val s = ByteSink(imageData.size + 4096)
        s.u8(0xFF); s.u8(0xD8)
        for (seg in newSegments) {
            if (seg.payload.size + 2 > 0xFFFF) throw MalformedImageException("Segmento JPEG excede 64 KB")
            s.u8(0xFF); s.u8(seg.marker); s.u16be(seg.payload.size + 2); s.write(seg.payload)
        }
        s.write(imageData)
        return s.toByteArray()
    }

    /** Width/height from the first SOFn segment. */
    fun dimensions(): Pair<Int, Int>? = segments.firstOrNull { it.isFrame() }?.payload?.let {
        if (it.size >= 5) it.u16be(3) to it.u16be(1) else null
    }

    /** False when the entropy-coded data has no EOI marker (file truncated). */
    fun hasEndMarker(): Boolean {
        val d = imageData
        for (i in 0 until d.size - 1) if (d[i] == 0xFF.toByte() && d[i + 1] == 0xD9.toByte()) return true
        return false
    }

    fun components(): Int? = segments.firstOrNull { it.isFrame() }?.payload?.takeIf { it.size >= 6 }?.u8(5)

    companion object {
        val EXIF_HEADER = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
        val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)
        val XMP_EXT_HEADER = "http://ns.adobe.com/xmp/extension/\u0000".toByteArray(Charsets.ISO_8859_1)
        val ICC_HEADER = "ICC_PROFILE\u0000".toByteArray(Charsets.ISO_8859_1)
        val MPF_HEADER = "MPF\u0000".toByteArray(Charsets.ISO_8859_1)
        val PHOTOSHOP_HEADER = "Photoshop 3.0\u0000".toByteArray(Charsets.ISO_8859_1)
        val SOF_MARKERS = setOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)

        fun parse(data: ByteArray): JpegFile {
            if (data.size < 4 || data.u8(0) != 0xFF || data.u8(1) != 0xD8) throw MalformedImageException("Não é um JPEG válido")
            val segs = mutableListOf<Segment>()
            var p = 2
            while (true) {
                if (p >= data.size) throw MalformedImageException("JPEG truncado: dados de imagem (SOS) não encontrados")
                if (data.u8(p) != 0xFF) throw MalformedImageException("Marcador JPEG inválido na posição $p")
                var marker = data.u8(p + 1)
                while (marker == 0xFF) { p++; marker = data.u8(p + 1) } // fill bytes
                if (marker == 0xDA) {
                    if (segs.none { it.isFrame() }) throw MalformedImageException("JPEG sem cabeçalho de quadro (SOF)")
                    return JpegFile(segs, data.copyOfRange(p, data.size))
                }
                if (marker == 0xD9) throw MalformedImageException("JPEG termina antes dos dados de imagem")
                if (marker in 0xD0..0xD7 || marker == 0x01) { p += 2; continue }
                if (p + 4 > data.size) throw MalformedImageException("JPEG truncado")
                val len = data.u16be(p + 2)
                if (len < 2 || p + 2 + len > data.size) throw MalformedImageException("Segmento JPEG truncado (0x%02X)".format(marker))
                segs += Segment(marker, data.copyOfRange(p + 4, p + 2 + len))
                p += 2 + len
            }
        }
    }
}
