package com.cleanshot.processing.c2pa

import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.processing.container.BmffFile
import com.cleanshot.processing.container.JpegFile
import com.cleanshot.processing.container.PngFile
import com.cleanshot.processing.container.WebpFile
import com.cleanshot.processing.util.u16be
import com.cleanshot.processing.util.u32be
import java.io.ByteArrayOutputStream

/** Extracts the embedded C2PA manifest store (JUMBF bytes) from each supported container. */
object C2paLocator {

    sealed interface Result {
        data class Found(val jumbf: ByteArray, val location: String) : Result
        data object NotFound : Result
        data class Unsupported(val reason: String) : Result
    }

    fun locate(bytes: ByteArray, format: ImageFormat): Result = when (format) {
        ImageFormat.JPEG -> fromJpeg(bytes)
        ImageFormat.PNG -> PngFile.parse(bytes).chunks.firstOrNull { it.type == "caBX" }
            ?.let { Result.Found(it.data, "Chunk PNG caBX") } ?: Result.NotFound
        ImageFormat.WEBP -> WebpFile.parse(bytes).chunks.firstOrNull { it.fourcc == "C2PA" }
            ?.let { Result.Found(it.data, "Chunk WebP C2PA") } ?: Result.NotFound
        ImageFormat.HEIF, ImageFormat.AVIF -> fromBmff(bytes)
        else -> Result.Unsupported("Localização de manifestos C2PA embutidos não implementada para ${format.displayName}.")
    }

    private fun fromJpeg(bytes: ByteArray): Result {
        val segs = JpegFile.parse(bytes).segments.filter { it.isJumbf() && it.payload.size >= 16 }
        if (segs.isEmpty()) return Result.NotFound
        // Group by box instance (En); concatenate in packet sequence (Z) order.
        val groups = segs.groupBy { it.payload.u16be(2) }
        for ((_, list) in groups) {
            val ordered = list.sortedBy { it.payload.u32be(4) }
            val out = ByteArrayOutputStream()
            for ((i, s) in ordered.withIndex()) {
                val p = s.payload
                if (i == 0) out.write(p, 8, p.size - 8)
                else {
                    val lbox = p.u32be(8)
                    val header = if (lbox == 1L) 16 else 8
                    out.write(p, 8 + header, p.size - 8 - header)
                }
            }
            val jumbf = out.toByteArray()
            if (jumbf.size >= 8 && String(jumbf, 4, 4, Charsets.ISO_8859_1) == "jumb") {
                val boxes = runCatching { Jumbf.parseAll(jumbf) }.getOrNull()
                if (boxes?.firstOrNull()?.label == "c2pa") return Result.Found(jumbf, "Segmentos JPEG APP11 (${ordered.size})")
            }
        }
        return Result.NotFound
    }

    private fun fromBmff(bytes: ByteArray): Result {
        val bmff = BmffFile.parse(bytes)
        val box = bmff.topLevel.firstOrNull { it.type == "uuid" && it.userType?.contentEquals(BmffFile.C2PA_UUID) == true }
            ?: return Result.NotFound
        var p = box.contentStart + 4 // version + flags
        val purposeStart = p
        while (p < box.end && bytes[p] != 0.toByte()) p++
        val purpose = String(bytes, purposeStart, p - purposeStart, Charsets.UTF_8)
        p++
        if (purpose != "manifest") return Result.Unsupported("Box C2PA com finalidade '$purpose' não suportada.")
        p += 8 // merkle offset
        if (p >= box.end) return Result.NotFound
        return Result.Found(bytes.copyOfRange(p, box.end), "Box ISOBMFF uuid C2PA")
    }
}
