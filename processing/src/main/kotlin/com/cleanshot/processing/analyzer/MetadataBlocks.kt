package com.cleanshot.processing.analyzer

import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.processing.catalog.PngTextCatalog
import com.cleanshot.processing.container.BmffFile
import com.cleanshot.processing.container.JpegFile
import com.cleanshot.processing.container.PngFile
import com.cleanshot.processing.container.WebpFile
import com.cleanshot.processing.iptc.IptcProcessor
import com.cleanshot.processing.util.startsWith
import com.cleanshot.processing.util.u32be
import java.io.ByteArrayOutputStream

/** Raw metadata blocks found in a container, plus structural facts. */
class MetadataBlocks(
    val exif: List<ByteArray>,
    val iptcIim: List<ByteArray>,
    val xmp: List<ByteArray>,
    val extendedXmp: ByteArray?,
    val icc: ByteArray?,
    val comments: List<String>,
    val pngTexts: List<PngFile.TextChunk>,
    val pngTime: String?,
    val width: Int?,
    val height: Int?,
    val hasAlpha: Boolean?,
    val animated: Boolean?,
    val bitDepth: Int?,
    val notes: List<String>,
    val unknownSegments: List<String>,
) {
    companion object {
        const val TRUNCATED_JPEG = "O JPEG parece truncado: o marcador de fim de imagem (EOI) não foi encontrado."
        private val EXIF_PREFIX = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)

        fun read(bytes: ByteArray, format: ImageFormat): MetadataBlocks = when (format) {
            ImageFormat.JPEG -> jpeg(bytes)
            ImageFormat.PNG -> png(bytes)
            ImageFormat.WEBP -> webp(bytes)
            ImageFormat.HEIF, ImageFormat.AVIF -> bmff(bytes)
            else -> MetadataBlocks(emptyList(), emptyList(), emptyList(), null, null, emptyList(), emptyList(), null, null, null, null, null, null, emptyList(), emptyList())
        }

        private fun jpeg(bytes: ByteArray): MetadataBlocks {
            val j = JpegFile.parse(bytes)
            val exif = j.segments.filter { it.isExif() }.map { it.payload.copyOfRange(6, it.payload.size) }
            val xmp = j.segments.filter { it.isXmp() }.map { it.payload.copyOfRange(JpegFile.XMP_HEADER.size, it.payload.size) }
            val iptc = mutableListOf<ByteArray>()
            val ps = j.segments.filter { it.isPhotoshop() }
            if (ps.isNotEmpty()) {
                val irb = ByteArrayOutputStream()
                ps.forEach { irb.write(it.payload, JpegFile.PHOTOSHOP_HEADER.size, it.payload.size - JpegFile.PHOTOSHOP_HEADER.size) }
                runCatching { IptcProcessor().parseIrb(irb.toByteArray()) }.getOrNull()
                    ?.filter { it.id == IptcProcessor.IPTC_ID }?.forEach { iptc += it.data }
            }
            val icc = j.segments.filter { it.isIcc() }.sortedBy { it.payload[12].toInt() and 0xFF }
                .takeIf { it.isNotEmpty() }?.let { segs ->
                    val o = ByteArrayOutputStream(); segs.forEach { o.write(it.payload, 14, it.payload.size - 14) }; o.toByteArray()
                }
            val comments = j.segments.filter { it.isComment() }.map { String(it.payload, Charsets.UTF_8).trimEnd('\u0000') }
            val notes = mutableListOf<String>()
            if (!j.hasEndMarker()) notes += TRUNCATED_JPEG
            if (j.segments.any { it.isMpf() }) notes += "Imagens secundárias MPF (ex.: mapa de ganho HDR) são preservadas sem alteração; metadados internos delas não são processados."
            val unknown = j.segments.filter {
                it.isApp && !it.isExif() && !it.isXmp() && !it.isExtendedXmp() && !it.isIcc() && !it.isMpf() && !it.isPhotoshop() && !it.isJumbf() &&
                    it.marker != 0xE0 && it.marker != 0xEE
            }.map { seg ->
                val id = String(seg.payload, 0, minOf(seg.payload.size, 12), Charsets.ISO_8859_1).takeWhile { c -> c.code in 0x20..0x7E }
                "APP${seg.marker - 0xE0}" + if (id.isNotBlank()) " ($id)" else ""
            }
            val (w, h) = j.dimensions() ?: (null to null)
            return MetadataBlocks(exif, iptc, xmp, extendedXmp(j), icc, comments, emptyList(), null, w, h, false, false, null, notes, unknown)
        }

        /** Reassembles Extended XMP (split across APP1 segments). */
        private fun extendedXmp(j: JpegFile): ByteArray? {
            val segs = j.segments.filter { it.isExtendedXmp() }
            if (segs.isEmpty()) return null
            val h = JpegFile.XMP_EXT_HEADER.size
            val first = segs.first().payload
            if (first.size < h + 40) return null
            val total = first.u32be(h + 32).toInt()
            if (total <= 0 || total > 64 * 1024 * 1024) return null
            val out = ByteArray(total)
            for (s in segs) {
                val p = s.payload
                if (p.size < h + 40) continue
                val off = p.u32be(h + 36).toInt()
                val len = p.size - h - 40
                if (off < 0 || off + len > total) return null
                System.arraycopy(p, h + 40, out, off, len)
            }
            return out
        }

        private fun png(bytes: ByteArray): MetadataBlocks {
            val png = PngFile.parse(bytes)
            val exif = png.chunks.filter { it.type == "eXIf" }.map { it.data }
            val texts = png.chunks.filter { it.type in PngFile.TEXT_CHUNKS }.mapNotNull { PngFile.decodeText(it) }
            val xmp = texts.filter { it.keyword == PngTextCatalog.XMP_KEYWORD }.map { it.text.toByteArray(Charsets.UTF_8) }
            val icc = png.chunks.firstOrNull { it.type == "iCCP" }?.data
            val time = png.chunks.firstOrNull { it.type == "tIME" }?.data?.takeIf { it.size == 7 }?.let {
                "%04d-%02d-%02d %02d:%02d:%02d".format(((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF), it[2].toInt(), it[3].toInt(), it[4].toInt(), it[5].toInt(), it[6].toInt())
            }
            val (w, h) = png.dimensions() ?: (null to null)
            val notes = mutableListOf<String>()
            if (png.isAnimated()) notes += "PNG animado (APNG): todos os quadros são preservados."
            return MetadataBlocks(exif, emptyList(), xmp, null, icc, emptyList(), texts.filter { it.keyword != PngTextCatalog.XMP_KEYWORD }, time,
                w, h, png.hasAlpha(), png.isAnimated(), png.bitDepth(), notes, emptyList())
        }

        private fun webp(bytes: ByteArray): MetadataBlocks {
            val w = WebpFile.parse(bytes)
            val exif = w.chunks.filter { it.fourcc == "EXIF" }.map { if (it.data.startsWith(EXIF_PREFIX)) it.data.copyOfRange(6, it.data.size) else it.data }
            val xmp = w.chunks.filter { it.fourcc == "XMP " }.map { it.data }
            val icc = w.chunks.firstOrNull { it.fourcc == "ICCP" }?.data
            val (wd, ht) = w.dimensions() ?: (null to null)
            val notes = mutableListOf<String>()
            if (w.isAnimated) notes += "WebP animado: todos os quadros (ANMF) são preservados."
            notes += if (w.chunks.any { it.fourcc == "VP8L" }) "WebP sem perdas (VP8L)." else "WebP com compressão com perdas (VP8)."
            val unknown = w.chunks.map { it.fourcc }.filter { it !in setOf("VP8X", "VP8 ", "VP8L", "ALPH", "ANIM", "ANMF", "ICCP", "EXIF", "XMP ", "C2PA") }
            return MetadataBlocks(exif, emptyList(), xmp, null, icc, emptyList(), emptyList(), null, wd, ht, w.hasAlpha(), w.isAnimated, null, notes, unknown)
        }

        private fun bmff(bytes: ByteArray): MetadataBlocks {
            val b = BmffFile.parse(bytes)
            val exif = b.exifItems().filter { it.extents.isNotEmpty() }.mapNotNull { item ->
                val payload = b.itemBytes(item)
                if (payload.size < 4) return@mapNotNull null
                val off = 4 + payload.u32be(0).toInt()
                if (off < 4 || off >= payload.size) null else payload.copyOfRange(off, payload.size)
            }
            val xmp = b.xmpItems().filter { it.extents.isNotEmpty() && it.contentEncoding.isNullOrEmpty() }.map { b.itemBytes(it) }
            val notes = mutableListOf<String>()
            if (b.items.any { it.constructionMethod < 0 }) notes += "Alguns itens usam referências externas ou métodos de construção não suportados; foram ignorados."
            notes += "Marcas do arquivo: ${b.brands.joinToString(", ")}"
            val dims = b.primaryDimensions()
            return MetadataBlocks(exif, emptyList(), xmp, null, null, emptyList(), emptyList(), null, dims?.first, dims?.second, b.hasAlphaItem(), null, null, notes, emptyList())
        }
    }
}
