package com.cleanshot.processing.cleaner

import com.cleanshot.domain.engine.ImagePreservationEngine
import com.cleanshot.domain.engine.PrivacyCleaner
import com.cleanshot.domain.model.AnalysisReport
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningPlan
import com.cleanshot.domain.model.CleaningReport
import com.cleanshot.domain.model.CleaningSupport
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.FileChange
import com.cleanshot.domain.model.FindingStatus
import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.domain.model.RemovalPolicy
import com.cleanshot.domain.model.UnsafeOperationException
import com.cleanshot.processing.ai.AiDeclarationDetector
import com.cleanshot.processing.analyzer.DefaultMetadataAnalyzer
import com.cleanshot.processing.catalog.PngTextCatalog
import com.cleanshot.processing.container.BmffFile
import com.cleanshot.processing.container.JpegFile
import com.cleanshot.processing.container.PngFile
import com.cleanshot.processing.container.WebpFile
import com.cleanshot.processing.exif.ExifProcessor
import com.cleanshot.processing.iptc.IptcProcessor
import com.cleanshot.processing.preservation.DefaultImagePreservationEngine
import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.util.startsWith
import com.cleanshot.processing.util.u32be
import com.cleanshot.processing.xmp.XmpProcessor
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Removes conventional metadata without re-encoding pixels.
 *
 * Every run: reads the original (never written), builds the new container in memory, verifies
 * the result with [ImagePreservationEngine] and only then writes [output]. Any failed check
 * aborts the operation with [UnsafeOperationException].
 */
class DefaultPrivacyCleaner(
    private val analyzer: DefaultMetadataAnalyzer = DefaultMetadataAnalyzer(),
    private val preservation: ImagePreservationEngine = DefaultImagePreservationEngine(),
    private val exif: ExifProcessor = ExifProcessor(),
    private val iptc: IptcProcessor = IptcProcessor(),
    private val xmp: XmpProcessor = XmpProcessor(),
) : PrivacyCleaner {

    private class Work(val changes: MutableList<String> = mutableListOf(), val warnings: MutableList<String> = mutableListOf())

    override fun plan(report: AnalysisReport, options: CleaningOptions): CleaningPlan {
        val format = report.file.format
        val found = report.foundEntries
        val selected = { e: MetadataEntry -> e.cleaningTarget != null && e.cleaningTarget in options.targets }
        val toRemove = found.filter { it.removal == RemovalPolicy.REMOVABLE && selected(it) }
        val protected = found.filter { it.removal == RemovalPolicy.PROTECTED_PROVENANCE || it.removal == RemovalPolicy.PROTECTED_RENDERING }
        val unsupported = found.filter { it.removal == RemovalPolicy.NOT_SUPPORTED }
        val preserve = found - toRemove.toSet() - protected.toSet() - unsupported.toSet()
        val warnings = mutableListOf<String>()
        if (report.provenance.c2paPresent) {
            warnings += "Este arquivo possui credenciais C2PA. O manifesto será preservado intacto, mas qualquer alteração no arquivo " +
                "invalida a vinculação (hash) assinada: verificadores indicarão que o arquivo foi modificado após a assinatura."
        }
        if (unsupported.isNotEmpty()) warnings += "${unsupported.size} bloco(s) não podem ser editados seletivamente e serão mantidos sem alteração."
        warnings += report.warnings.filter { "MPF" in it }
        val (can, reason) = when {
            report.warnings.contains(com.cleanshot.processing.analyzer.MetadataBlocks.TRUNCATED_JPEG) ->
                false to "O arquivo original está incompleto (truncado). O CleanShot não gera cópias de arquivos danificados."
            !format.canClean -> false to "O formato ${format.displayName} é suportado apenas para análise. O CleanShot não converte nem recodifica imagens silenciosamente."
            options.isEmpty -> false to "Nenhum grupo de metadados foi selecionado."
            toRemove.isEmpty() -> false to "Nenhum dos metadados selecionados foi encontrado neste arquivo."
            else -> true to null
        }
        return CleaningPlan(format, can, reason, toRemove, preserve, protected, unsupported, strategy(format), warnings, requiresReencoding = false)
    }

    private fun strategy(format: ImageFormat): String = when (format) {
        ImageFormat.JPEG -> "Reescrita de segmentos JPEG: blocos EXIF/XMP/IPTC são reconstruídos e os dados de imagem comprimidos são copiados byte a byte (sem recodificação)."
        ImageFormat.PNG -> "Reescrita de chunks PNG: chunks de texto/EXIF são filtrados com novo CRC; IDAT, transparência e perfil de cor são copiados sem alteração."
        ImageFormat.WEBP -> "Reescrita de chunks RIFF: EXIF/XMP são filtrados e as flags VP8X atualizadas; VP8/VP8L/ALPH/ANMF são copiados sem alteração."
        ImageFormat.HEIF, ImageFormat.AVIF -> "Reescrita no lugar: os itens Exif/XMP são regravados com o mesmo tamanho (preenchimento neutro). Nenhuma outra parte do arquivo é tocada e o formato é mantido."
        else -> "Sem suporte a escrita."
    }

    override fun clean(input: File, output: File, displayName: String, options: CleaningOptions): CleaningReport {
        if (input.canonicalPath == output.canonicalPath) throw UnsafeOperationException("O arquivo de saída não pode ser o original")
        val original = input.readBytes()
        val before = analyzer.analyzeBytes(original, displayName)
        val format = before.file.format
        val plan = plan(before, options)
        if (!plan.canProceed) throw UnsafeOperationException(plan.blockingReason ?: "Operação não permitida")
        val work = Work()
        val targets = options.targets
        val result = try {
            when (format.cleaningSupport) {
                CleaningSupport.LOSSLESS_REWRITE -> when (format) {
                    ImageFormat.JPEG -> jpeg(original, targets, work)
                    ImageFormat.PNG -> png(original, targets, work)
                    ImageFormat.WEBP -> webp(original, targets, work)
                    else -> throw UnsafeOperationException("Formato sem estratégia")
                }
                CleaningSupport.IN_PLACE_ONLY -> bmff(original, targets, work)
                else -> throw UnsafeOperationException("Formato sem suporte a escrita")
            }
        } catch (e: MalformedImageException) {
            throw UnsafeOperationException("Não foi possível garantir um resultado seguro: ${e.message}")
        }

        val checks = preservation.verify(original, result, format)
        val failed = checks.filterNot { it.passed }
        if (failed.isNotEmpty()) {
            throw UnsafeOperationException("Operação interrompida: a verificação de preservação falhou (" + failed.joinToString { "${it.name}: ${it.detail}" } + ")")
        }

        val after = try { analyzer.analyzeBytes(result, displayName) } catch (e: Exception) {
            throw UnsafeOperationException("O arquivo gerado não pôde ser relido: ${e.message}")
        }

        // Atomic write: temp file in the destination folder, then rename.
        val tmp = File(output.parentFile, ".${output.name}.part")
        try {
            tmp.outputStream().use { it.write(result); it.fd.sync() }
            if (output.exists() && !output.delete()) throw UnsafeOperationException("Não foi possível substituir ${output.name}")
            if (!tmp.renameTo(output)) throw UnsafeOperationException("Não foi possível finalizar a gravação")
        } finally {
            tmp.delete()
        }

        val afterKeys = after.foundEntries.map { it.key }.toSet()
        val beforeFound = before.foundEntries.filter { it.category != com.cleanshot.domain.model.MetadataCategory.FILE }
        val removed = beforeFound.filter { it.key !in afterKeys }
        val preserved = beforeFound.filter { it.key in afterKeys }
        val notRemoved = plan.toRemove.filter { it.key in afterKeys }
        if (notRemoved.isNotEmpty()) work.warnings += "Campos que permaneceram após o processamento: " + notRemoved.joinToString { it.name }
        return CleaningReport(
            originalName = displayName,
            format = format,
            originalSize = original.size.toLong(),
            resultSize = result.size.toLong(),
            found = beforeFound,
            removed = removed,
            preserved = preserved,
            unsupported = plan.unsupported,
            changes = work.changes.map { FileChange(it) },
            preservationChecks = checks,
            reencoded = false,
            warnings = (plan.warnings + work.warnings).distinct(),
        )
    }

    // ---------------------------------------------------------------- JPEG

    private fun jpeg(bytes: ByteArray, targets: Set<CleaningTarget>, w: Work): ByteArray {
        val j = JpegFile.parse(bytes)
        val out = mutableListOf<JpegFile.Segment>()
        val ps = j.segments.filter { it.isPhotoshop() }
        var photoshopDone = false
        for (seg in j.segments) {
            when {
                seg.isExif() -> {
                    val r = exif.clean(seg.payload.copyOfRange(6, seg.payload.size), targets)
                    w.changes += r.changes; w.warnings += r.warnings
                    if (r.tiff != null) out += JpegFile.Segment(0xE1, JpegFile.EXIF_HEADER + r.tiff)
                    else w.changes += "Segmento APP1/EXIF removido (vazio)"
                }
                seg.isXmp() -> {
                    val r = xmp.clean(seg.payload.copyOfRange(JpegFile.XMP_HEADER.size, seg.payload.size), targets)
                    if (r.removedKeys.isNotEmpty()) w.changes += "XMP: ${r.removedKeys.size} propriedade(s) removida(s)"
                    if (r.xmp != null) {
                        val payload = JpegFile.XMP_HEADER + r.xmp
                        if (payload.size > 65533) throw MalformedImageException("XMP resultante excede o limite do segmento JPEG")
                        out += JpegFile.Segment(0xE1, payload)
                    } else w.changes += "Segmento APP1/XMP removido (vazio)"
                }
                seg.isPhotoshop() -> {
                    if (photoshopDone) continue
                    photoshopDone = true
                    val irb = ByteArrayOutputStream()
                    ps.forEach { irb.write(it.payload, JpegFile.PHOTOSHOP_HEADER.size, it.payload.size - JpegFile.PHOTOSHOP_HEADER.size) }
                    val r = iptc.cleanIrb(irb.toByteArray(), targets)
                    w.changes += r.changes
                    val payload = r.payload
                    if (payload == null) { w.changes += "Segmento APP13/Photoshop removido (vazio)"; continue }
                    // Split back into ≤64 KB segments.
                    val max = 65533 - JpegFile.PHOTOSHOP_HEADER.size
                    var p = 0
                    while (p < payload.size) {
                        val n = minOf(max, payload.size - p)
                        out += JpegFile.Segment(0xED, JpegFile.PHOTOSHOP_HEADER + payload.copyOfRange(p, p + n))
                        p += n
                    }
                }
                seg.isComment() && CleaningTarget.COMMENTS in targets -> {
                    val text = String(seg.payload, Charsets.UTF_8)
                    if (AiDeclarationDetector.detect(text) != null) out += seg
                    else w.changes += "Comentário JPEG (COM) removido"
                }
                else -> out += seg
            }
        }
        return j.write(out)
    }

    // ---------------------------------------------------------------- PNG

    private fun png(bytes: ByteArray, targets: Set<CleaningTarget>, w: Work): ByteArray {
        val png = PngFile.parse(bytes)
        val out = mutableListOf<PngFile.Chunk>()
        for (c in png.chunks) {
            when {
                c.type == "eXIf" -> {
                    val r = exif.clean(c.data, targets)
                    w.changes += r.changes; w.warnings += r.warnings
                    when {
                        r.removedKeys.isEmpty() -> out += c
                        r.tiff != null -> out += PngFile.Chunk("eXIf", r.tiff)
                        else -> w.changes += "Chunk eXIf removido (vazio)"
                    }
                }
                c.type in PngFile.TEXT_CHUNKS -> {
                    val t = PngFile.decodeText(c)
                    if (t == null) { out += c; continue }
                    if (t.keyword == PngTextCatalog.XMP_KEYWORD) {
                        val r = xmp.clean(t.text.toByteArray(Charsets.UTF_8), targets)
                        when {
                            r.removedKeys.isEmpty() -> out += c
                            r.xmp != null -> { out += PngFile.iTxt(t.keyword, r.xmp); w.changes += "XMP (iTXt): ${r.removedKeys.size} propriedade(s) removida(s)" }
                            else -> w.changes += "Chunk XMP removido (vazio)"
                        }
                        continue
                    }
                    val info = PngTextCatalog.info(t.keyword)
                    val protected = t.keyword in AiDeclarationDetector.AI_PNG_KEYWORDS || AiDeclarationDetector.detect(t.text) != null
                    if (!protected && info.target != null && info.target in targets) w.changes += "Texto PNG \"${t.keyword}\" (${c.type}) removido"
                    else out += c
                }
                c.type == "tIME" && CleaningTarget.DATES in targets -> w.changes += "Chunk tIME removido"
                else -> out += c
            }
        }
        return png.write(out)
    }

    // ---------------------------------------------------------------- WebP

    private fun webp(bytes: ByteArray, targets: Set<CleaningTarget>, w: Work): ByteArray {
        val webp = WebpFile.parse(bytes)
        val out = mutableListOf<WebpFile.Chunk>()
        var clearFlags = 0
        val prefix = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
        for (c in webp.chunks) {
            when (c.fourcc) {
                "EXIF" -> {
                    val hasPrefix = c.data.startsWith(prefix)
                    val r = exif.clean(if (hasPrefix) c.data.copyOfRange(6, c.data.size) else c.data, targets)
                    w.changes += r.changes; w.warnings += r.warnings
                    when {
                        r.removedKeys.isEmpty() -> out += c
                        r.tiff != null -> out += WebpFile.Chunk("EXIF", if (hasPrefix) prefix + r.tiff else r.tiff)
                        else -> { clearFlags = clearFlags or WebpFile.FLAG_EXIF; w.changes += "Chunk EXIF removido (vazio)" }
                    }
                }
                "XMP " -> {
                    val r = xmp.clean(c.data, targets)
                    when {
                        r.removedKeys.isEmpty() -> out += c
                        r.xmp != null -> { out += WebpFile.Chunk("XMP ", r.xmp); w.changes += "XMP: ${r.removedKeys.size} propriedade(s) removida(s)" }
                        else -> { clearFlags = clearFlags or WebpFile.FLAG_XMP; w.changes += "Chunk XMP removido (vazio)" }
                    }
                }
                else -> out += c
            }
        }
        if (clearFlags != 0) {
            val idx = out.indexOfFirst { it.fourcc == "VP8X" }
            if (idx >= 0) {
                val d = out[idx].data.copyOf()
                d[0] = (d[0].toInt() and clearFlags.inv()).toByte()
                out[idx] = WebpFile.Chunk("VP8X", d)
                w.changes += "Flags VP8X atualizadas"
            }
        }
        return webp.write(out)
    }

    // ---------------------------------------------------------------- HEIF / AVIF

    private fun bmff(bytes: ByteArray, targets: Set<CleaningTarget>, w: Work): ByteArray {
        val src = BmffFile.parse(bytes)
        val result = bytes.copyOf()
        for (item in src.exifItems()) {
            if (item.extents.isEmpty()) throw MalformedImageException("Item Exif com referência não suportada")
            val payload = src.itemBytes(item)
            if (payload.size < 4) continue
            val tiffStart = 4 + payload.u32be(0).toInt()
            if (tiffStart !in 4 until payload.size) throw MalformedImageException("Cabeçalho do item Exif inválido")
            val tiff = payload.copyOfRange(tiffStart, payload.size)
            val r = exif.clean(tiff, targets)
            if (r.removedKeys.isEmpty()) continue
            w.changes += r.changes; w.warnings += r.warnings
            val newTiff = r.tiff ?: EMPTY_TIFF
            if (newTiff.size > tiff.size) throw MalformedImageException("EXIF reescrito não cabe no espaço original")
            val newPayload = ByteArray(payload.size)
            System.arraycopy(payload, 0, newPayload, 0, tiffStart)
            System.arraycopy(newTiff, 0, newPayload, tiffStart, newTiff.size)
            src.writeItemInPlace(result, item, newPayload)
            w.changes += "Item Exif (id ${item.id}) regravado no lugar com ${tiff.size - newTiff.size} bytes de preenchimento neutro"
        }
        for (item in src.xmpItems()) {
            if (item.extents.isEmpty()) throw MalformedImageException("Item XMP com referência não suportada")
            if (!item.contentEncoding.isNullOrEmpty()) { w.warnings += "Item XMP comprimido não pode ser editado; mantido."; continue }
            val payload = src.itemBytes(item)
            val r = xmp.clean(payload, targets, exactLength = payload.size)
            if (r.removedKeys.isEmpty()) continue
            src.writeItemInPlace(result, item, r.xmp!!)
            w.changes += "Item XMP (id ${item.id}): ${r.removedKeys.size} propriedade(s) removida(s), regravado no lugar"
        }
        return result
    }

    private companion object {
        /** Big-endian TIFF header with an empty IFD0. */
        val EMPTY_TIFF = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    }
}
