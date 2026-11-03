package com.cleanshot.processing.format

import com.cleanshot.domain.engine.ImageFormatDetector
import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.processing.util.ascii
import com.cleanshot.processing.util.u32be

/**
 * Detects the real container from its signature. The file extension is only used to
 * distinguish DNG from plain TIFF, never to override the signature.
 */
class DefaultImageFormatDetector : ImageFormatDetector {

    override fun detect(header: ByteArray, fileName: String?): ImageFormat {
        val h = header
        if (h.size < 12) return ImageFormat.UNKNOWN
        return when {
            h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte() -> ImageFormat.JPEG
            h.ascii(0, 8) == "\u0089PNG\r\n\u001A\n" -> ImageFormat.PNG
            h.ascii(0, 4) == "RIFF" && h.ascii(8, 4) == "WEBP" -> ImageFormat.WEBP
            h.ascii(0, 6) == "GIF87a" || h.ascii(0, 6) == "GIF89a" -> ImageFormat.GIF
            h.ascii(0, 4) == "II*\u0000" || h.ascii(0, 4) == "MM\u0000*" ->
                if (fileName?.substringAfterLast('.', "")?.lowercase() == "dng") ImageFormat.DNG else ImageFormat.TIFF
            h.ascii(0, 2) == "BM" -> ImageFormat.BMP
            h.ascii(4, 4) == "ftyp" -> detectBmff(h)
            else -> ImageFormat.UNKNOWN
        }
    }

    private fun detectBmff(h: ByteArray): ImageFormat {
        val boxSize = h.u32be(0).toInt().coerceAtMost(h.size)
        val brands = buildList {
            add(h.ascii(8, 4))
            var p = 16
            while (p + 4 <= boxSize) { add(h.ascii(p, 4)); p += 4 }
        }
        return when {
            brands.first() == "avif" || brands.first() == "avis" -> ImageFormat.AVIF
            brands.any { it in HEIF_BRANDS } -> ImageFormat.HEIF
            brands.any { it == "avif" || it == "avis" } -> ImageFormat.AVIF
            else -> ImageFormat.UNKNOWN
        }
    }

    private companion object {
        val HEIF_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1", "mif2")
    }
}
