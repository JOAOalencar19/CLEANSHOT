package com.cleanshot.processing.preservation

import com.cleanshot.domain.engine.ImagePreservationEngine
import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.domain.model.PreservationCheck
import com.cleanshot.processing.c2pa.C2paLocator
import com.cleanshot.processing.container.BmffFile
import com.cleanshot.processing.container.JpegFile
import com.cleanshot.processing.container.PngFile
import com.cleanshot.processing.container.WebpFile
import com.cleanshot.processing.exif.IfdType
import com.cleanshot.processing.exif.TiffParser
import com.cleanshot.processing.util.sha256Hex
import java.io.ByteArrayOutputStream

/**
 * Proves, structurally, that a processed file kept the original image data:
 * pixel-carrying chunks are compared by SHA-256, as are colour profiles, orientation,
 * dimensions, transparency/animation flags and C2PA manifests.
 */
class DefaultImagePreservationEngine : ImagePreservationEngine {

    override fun verify(original: ByteArray, processed: ByteArray, format: ImageFormat): List<PreservationCheck> {
        val checks = try {
            when (format) {
                ImageFormat.JPEG -> jpeg(original, processed)
                ImageFormat.PNG -> png(original, processed)
                ImageFormat.WEBP -> webp(original, processed)
                ImageFormat.HEIF, ImageFormat.AVIF -> bmff(original, processed)
                else -> listOf(PreservationCheck("Formato suportado", false, "${format.displayName} não pode ser processado."))
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf(PreservationCheck("Estrutura do arquivo resultante", false, "O arquivo gerado não pôde ser lido: ${e.message}"))
        }
        checks += c2paCheck(original, processed, format)
        return checks
    }

    private fun same(name: String, a: ByteArray?, b: ByteArray?, okDetail: String, whatIfAbsent: String = "Não presente no original."): PreservationCheck = when {
        a == null && b == null -> PreservationCheck(name, true, whatIfAbsent)
        a == null || b == null -> PreservationCheck(name, false, "Presença divergente entre original e resultado.")
        a.contentEquals(b) -> PreservationCheck(name, true, "$okDetail (SHA-256 ${a.sha256Hex().take(16)}…)")
        else -> PreservationCheck(name, false, "Conteúdo divergente.")
    }

    private fun jpeg(o: ByteArray, p: ByteArray): List<PreservationCheck> {
        val a = JpegFile.parse(o); val b = JpegFile.parse(p)
        fun structural(j: JpegFile) = cat(j.segments.filter { (!it.isApp || it.marker == 0xEE) && !it.isComment() }.map { byteArrayOf(it.marker.toByte()) + it.payload })
        fun icc(j: JpegFile) = j.segments.filter { it.isIcc() }.takeIf { it.isNotEmpty() }?.let { s -> cat(s.map { it.payload }) }
        fun mpf(j: JpegFile) = j.segments.filter { it.isMpf() }.takeIf { it.isNotEmpty() }?.let { s -> cat(s.map { it.payload }) }
        return listOf(
            same("Dados de imagem comprimidos", a.imageData, b.imageData, "Idênticos byte a byte; nenhuma recodificação"),
            same("Tabelas de quantização/Huffman e quadro", structural(a), structural(b), "Idênticos"),
            PreservationCheck("Dimensões", a.dimensions() == b.dimensions(), "${a.dimensions()?.let { "${it.first}×${it.second}" }}"),
            same("Perfil de cores ICC", icc(a), icc(b), "Preservado"),
            same("Índice de imagens múltiplas (MPF)", mpf(a), mpf(b), "Preservado"),
            orientation(a.segments.firstOrNull { it.isExif() }?.payload?.let { it.copyOfRange(6, it.size) },
                b.segments.firstOrNull { it.isExif() }?.payload?.let { it.copyOfRange(6, it.size) }),
        )
    }

    private fun png(o: ByteArray, p: ByteArray): List<PreservationCheck> {
        val a = PngFile.parse(o); val b = PngFile.parse(p)
        fun img(f: PngFile) = cat(f.chunks.filter { it.isImageData }.map { it.type.toByteArray() + it.data })
        fun color(f: PngFile) = cat(f.chunks.filter { it.type in PngFile.COLOR_CHUNKS }.map { it.type.toByteArray() + it.data })
        return listOf(
            same("Dados de pixels (IHDR/IDAT/PLTE/tRNS/APNG)", img(a), img(b), "Idênticos byte a byte"),
            PreservationCheck("Transparência", a.hasAlpha() == b.hasAlpha(), if (a.hasAlpha()) "Canal alfa preservado" else "Imagem sem transparência"),
            PreservationCheck("Animação", a.isAnimated() == b.isAnimated(), if (a.isAnimated()) "Quadros APNG preservados" else "Imagem estática"),
            same("Gerenciamento de cor (iCCP/sRGB/gAMA/cHRM/cICP)", color(a).takeIf { it.isNotEmpty() }, color(b).takeIf { it.isNotEmpty() }, "Preservado"),
            orientation(a.chunks.firstOrNull { it.type == "eXIf" }?.data, b.chunks.firstOrNull { it.type == "eXIf" }?.data),
        )
    }

    private fun webp(o: ByteArray, p: ByteArray): List<PreservationCheck> {
        val a = WebpFile.parse(o); val b = WebpFile.parse(p)
        fun img(f: WebpFile) = cat(f.chunks.filter { it.fourcc in WebpFile.IMAGE_CHUNKS }.map { it.fourcc.toByteArray() + it.data })
        fun canvas(f: WebpFile) = f.chunks.firstOrNull { it.fourcc == "VP8X" }?.data?.let {
            byteArrayOf((it[0].toInt() and (WebpFile.FLAG_ALPHA or WebpFile.FLAG_ANIMATION or WebpFile.FLAG_ICC)).toByte()) + it.copyOfRange(4, 10)
        }
        return listOf(
            same("Dados de imagem (VP8/VP8L/ALPH/ANMF)", img(a), img(b), "Idênticos byte a byte"),
            same("Tela, alfa e animação (VP8X)", canvas(a), canvas(b), "Preservados", "Formato WebP simples."),
            PreservationCheck("Transparência", a.hasAlpha() == b.hasAlpha(), if (a.hasAlpha()) "Canal alfa preservado" else "Sem transparência"),
            same("Perfil de cores ICC", a.chunks.firstOrNull { it.fourcc == "ICCP" }?.data, b.chunks.firstOrNull { it.fourcc == "ICCP" }?.data, "Preservado"),
        )
    }

    private fun bmff(o: ByteArray, p: ByteArray): List<PreservationCheck> {
        val checks = mutableListOf<PreservationCheck>()
        checks += PreservationCheck("Tamanho e estrutura", o.size == p.size, if (o.size == p.size) "Mesmo tamanho; nenhuma caixa foi movida" else "Tamanho divergente")
        if (o.size != p.size) return checks
        val src = BmffFile.parse(o)
        val allowed = (src.exifItems() + src.xmpItems()).flatMap { it.extents }
        var outside = 0
        var i = 0
        while (i < o.size) {
            if (o[i] != p[i] && allowed.none { i >= it.offset && i < it.offset + it.length }) outside++
            i++
        }
        checks += PreservationCheck("Dados de imagem e propriedades", outside == 0,
            if (outside == 0) "Somente bytes dos itens Exif/XMP foram alterados; imagem, perfil de cor e mapas auxiliares intactos" else "$outside byte(s) alterados fora dos metadados")
        val dst = BmffFile.parse(p)
        checks += PreservationCheck("Dimensões", src.primaryDimensions() == dst.primaryDimensions(), src.primaryDimensions()?.let { "${it.first}×${it.second}" } ?: "—")
        return checks
    }

    private fun orientation(a: ByteArray?, b: ByteArray?): PreservationCheck {
        fun read(t: ByteArray?): ByteArray? = t?.let { runCatching { TiffParser.parse(it).entries(IfdType.IFD0).firstOrNull { e -> e.tag == 0x0112 }?.value }.getOrNull() }
        val oa = read(a); val ob = read(b)
        return when {
            oa == null -> PreservationCheck("Orientação", ob == null, "Sem tag de orientação no original")
            ob != null && oa.contentEquals(ob) -> PreservationCheck("Orientação", true, "Tag de orientação preservada")
            else -> PreservationCheck("Orientação", false, "A orientação foi perdida ou alterada")
        }
    }

    private fun c2paCheck(o: ByteArray, p: ByteArray, format: ImageFormat): PreservationCheck {
        val a = runCatching { C2paLocator.locate(o, format) }.getOrNull() as? C2paLocator.Result.Found
        val b = runCatching { C2paLocator.locate(p, format) }.getOrNull() as? C2paLocator.Result.Found
        return when {
            a == null -> PreservationCheck("Credenciais C2PA", true, "Nenhum manifesto no original")
            b != null && a.jumbf.contentEquals(b.jumbf) -> PreservationCheck("Credenciais C2PA", true, "Manifesto preservado byte a byte")
            else -> PreservationCheck("Credenciais C2PA", false, "O manifesto C2PA foi perdido ou alterado")
        }
    }

    private fun cat(parts: List<ByteArray>): ByteArray = ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
}
