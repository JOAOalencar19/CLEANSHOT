package com.cleanshot.processing.analyzer

import com.cleanshot.domain.engine.C2paInspector
import com.cleanshot.domain.engine.ImageFormatDetector
import com.cleanshot.domain.engine.MetadataAnalyzer
import com.cleanshot.domain.model.AiDeclaration
import com.cleanshot.domain.model.AnalysisReport
import com.cleanshot.domain.model.CheckState
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.FindingStatus
import com.cleanshot.domain.model.ImageFileInfo
import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.domain.model.MetadataSection
import com.cleanshot.domain.model.MetadataStandard
import com.cleanshot.domain.model.PrivacyLevel
import com.cleanshot.domain.model.ProvenanceReport
import com.cleanshot.domain.model.ProvenanceVerdict
import com.cleanshot.domain.model.RemovalPolicy
import com.cleanshot.processing.ai.AiDeclarationDetector
import com.cleanshot.processing.c2pa.DefaultC2paInspector
import com.cleanshot.processing.catalog.PngTextCatalog
import com.cleanshot.processing.exif.ExifProcessor
import com.cleanshot.processing.format.DefaultImageFormatDetector
import com.cleanshot.processing.iptc.IptcProcessor
import com.cleanshot.processing.util.MalformedImageException
import com.cleanshot.processing.xmp.XmpProcessor
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.GpsDirectory
import com.drew.metadata.icc.IccDirectory
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Reads every metadata family of an image. Uses CleanShot's own container parsers for the
 * formats it can clean (so analysis and cleaning share the same keys) and the
 * metadata-extractor library for colour profiles and analysis-only formats.
 */
class DefaultMetadataAnalyzer(
    private val detector: ImageFormatDetector = DefaultImageFormatDetector(),
    private val c2pa: C2paInspector = DefaultC2paInspector(),
    private val exif: ExifProcessor = ExifProcessor(),
    private val iptc: IptcProcessor = IptcProcessor(),
    private val xmp: XmpProcessor = XmpProcessor(),
) : MetadataAnalyzer {

    override fun analyze(file: File, displayName: String): AnalysisReport {
        if (!file.exists() || file.length() == 0L) throw MalformedImageException("Arquivo vazio ou inexistente")
        if (file.length() > MAX_BYTES) throw MalformedImageException("Arquivo maior que ${MAX_BYTES / (1024 * 1024)} MB não é suportado")
        return analyzeBytes(file.readBytes(), displayName)
    }

    fun analyzeBytes(bytes: ByteArray, displayName: String): AnalysisReport {
        val format = detector.detect(bytes.copyOfRange(0, minOf(bytes.size, 64)), displayName)
        if (format == ImageFormat.UNKNOWN) throw MalformedImageException("Formato de arquivo não reconhecido")
        val warnings = mutableListOf<String>()
        val entries = mutableListOf<MetadataEntry>()

        val blocks = MetadataBlocks.read(bytes, format)
        warnings += blocks.notes
        val libMetadata = runCatching { ImageMetadataReader.readMetadata(ByteArrayInputStream(bytes), bytes.size.toLong()) }.getOrNull()
        val iccName = libMetadata?.getFirstDirectoryOfType(IccDirectory::class.java)?.getDescription(IccDirectory.TAG_TAG_desc)
        var width = blocks.width
        var height = blocks.height

        if (format.canClean) {
            blocks.exif.forEach { tiff -> safely(warnings, "EXIF") { entries += exif.entries(tiff) } }
            blocks.iptcIim.forEach { iim -> safely(warnings, "IPTC") { entries += iptc.entries(iim) } }
            blocks.xmp.forEach { packet -> safely(warnings, "XMP") { entries += xmp.entries(packet) } }
            blocks.extendedXmp?.let { ext ->
                safely(warnings, "XMP estendido") {
                    entries += xmp.entries(ext).map {
                        it.copy(key = it.key.replaceFirst("XMP:", "XMPx:"), removal = if (it.removal == RemovalPolicy.REMOVABLE) RemovalPolicy.NOT_SUPPORTED else it.removal,
                            description = it.description + " (XMP estendido: preservado sem alteração.)")
                    }
                }
            }
            blocks.comments.forEachIndexed { i, c -> entries += commentEntry(i, c) }
            blocks.pngTexts.forEach { entries += pngTextEntry(it) }
            blocks.pngTime?.let {
                entries += MetadataEntry("PNG:tIME", "Última modificação (PNG)", it, MetadataStandard.PNG_TEXT, MetadataCategory.DATES,
                    "Data da última modificação gravada no chunk tIME.", PrivacyLevel.LOW, removal = RemovalPolicy.REMOVABLE, cleaningTarget = CleaningTarget.DATES)
            }
            blocks.unknownSegments.forEach { seg ->
                entries += MetadataEntry("SEG:$seg", "Bloco proprietário $seg", "Presente", MetadataStandard.CONTAINER, MetadataCategory.OTHER,
                    "Bloco de dados de aplicativo/fabricante não interpretado. É preservado sem alteração.", PrivacyLevel.LOW, removal = RemovalPolicy.NOT_SUPPORTED)
            }
        } else if (libMetadata != null) {
            entries += libraryEntries(libMetadata)
            warnings += "${format.displayName}: somente análise. CleanShot não modifica este formato para evitar conversões ou perda de dados."
        }
        if (blocks.icc != null || iccName != null) {
            entries += MetadataEntry("ICC:profile", "Perfil de cores ICC", iccName ?: "Presente", MetadataStandard.ICC, MetadataCategory.COLOR,
                "Define como as cores devem ser exibidas. Sempre preservado.", PrivacyLevel.NONE, removal = RemovalPolicy.PROTECTED_RENDERING,
                details = blocks.icc?.let { "%.1f KB".format(it.size / 1024.0) })
        }
        if (width == null && libMetadata != null) {
            val (w, h) = libraryDimensions(libMetadata); width = w; height = h
        }

        val provenance = provenance(bytes, format, entries, blocks)
        entries += provenanceEntries(provenance, format)
        entries += expectedFields(entries, format)

        val info = ImageFileInfo(displayName, bytes.size.toLong(), format, width, height, blocks.hasAlpha, blocks.animated, blocks.bitDepth, iccName)
        entries.addAll(0, fileEntries(info))

        val sections = entries.distinctBy { it.key }
            .groupBy { it.category }
            .map { (cat, list) -> MetadataSection(cat, list.sortedWith(compareBy<MetadataEntry> { it.status.ordinal }.thenByDescending { it.privacy.ordinal })) }
            .sortedBy { it.category.order }
        return AnalysisReport(info, sections, provenance, warnings.distinct())
    }

    private inline fun safely(warnings: MutableList<String>, what: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { warnings += "$what ilegível ou corrompido: ${e.message}" }
    }

    private fun commentEntry(i: Int, text: String): MetadataEntry {
        val ai = AiDeclarationDetector.detect(text)
        return MetadataEntry(
            key = "COM:$i", name = "Comentário JPEG", value = text.take(2000), standard = MetadataStandard.JPEG_COMMENT,
            category = if (ai != null) MetadataCategory.PROVENANCE else MetadataCategory.DESCRIPTION,
            description = "Comentário livre gravado no arquivo." + if (ai != null) " Contém declaração relacionada a IA; será preservado." else "",
            privacy = PrivacyLevel.MEDIUM,
            removal = if (ai != null) RemovalPolicy.PROTECTED_PROVENANCE else RemovalPolicy.REMOVABLE,
            cleaningTarget = if (ai != null) null else CleaningTarget.COMMENTS,
        )
    }

    private fun pngTextEntry(t: com.cleanshot.processing.container.PngFile.TextChunk): MetadataEntry {
        val info = PngTextCatalog.info(t.keyword)
        val aiKeyword = t.keyword in AiDeclarationDetector.AI_PNG_KEYWORDS
        val ai = aiKeyword || (info.target != null && AiDeclarationDetector.detect(t.text) != null)
        val rawProfile = t.keyword.lowercase().startsWith("raw profile type")
        return MetadataEntry(
            key = "PNG:${t.keyword}",
            name = if (aiKeyword) "Parâmetros de geração (${t.keyword})" else info.name,
            value = if (rawProfile) "${t.text.length} caracteres codificados" else t.text.take(2000),
            standard = MetadataStandard.PNG_TEXT,
            category = if (ai) MetadataCategory.PROVENANCE else info.category,
            description = if (ai) "Texto gravado por ferramentas de geração de imagem (prompt, parâmetros ou fluxo de trabalho). É uma declaração de origem e será preservado."
            else info.description,
            privacy = if (rawProfile) PrivacyLevel.MEDIUM else info.privacy,
            removal = when {
                ai -> RemovalPolicy.PROTECTED_PROVENANCE
                rawProfile -> RemovalPolicy.NOT_SUPPORTED
                info.target != null -> RemovalPolicy.REMOVABLE
                else -> RemovalPolicy.NOT_APPLICABLE
            },
            cleaningTarget = if (ai) null else info.target,
            details = "Chunk ${t.kind}",
        )
    }

    private fun libraryEntries(md: Metadata): List<MetadataEntry> = md.directories.flatMap { dir ->
        val isGps = dir is GpsDirectory
        dir.tags.mapNotNull { tag ->
            val desc = tag.description ?: return@mapNotNull null
            val name = tag.tagName
            val lower = name.lowercase()
            val (cat, privacy) = when {
                isGps -> MetadataCategory.LOCATION to PrivacyLevel.HIGH
                "serial" in lower || "owner" in lower -> MetadataCategory.DEVICE to PrivacyLevel.HIGH
                "artist" in lower || "author" in lower || "creator" in lower || "by-line" in lower -> MetadataCategory.AUTHORSHIP to PrivacyLevel.HIGH
                "copyright" in lower -> MetadataCategory.AUTHORSHIP to PrivacyLevel.MEDIUM
                "date" in lower || "time" in lower -> MetadataCategory.DATES to PrivacyLevel.LOW
                "make" == lower || "model" == lower || "lens" in lower -> MetadataCategory.DEVICE to PrivacyLevel.LOW
                "software" in lower -> MetadataCategory.SOFTWARE to PrivacyLevel.LOW
                "comment" in lower || "description" in lower || "caption" in lower -> MetadataCategory.DESCRIPTION to PrivacyLevel.MEDIUM
                dir.name.startsWith("Exif") -> MetadataCategory.CAPTURE to PrivacyLevel.NONE
                else -> MetadataCategory.OTHER to PrivacyLevel.NONE
            }
            MetadataEntry(
                key = "LIB:${dir.name}:${tag.tagType}", name = name, value = desc.take(2000),
                standard = standardOf(dir.name), category = cat,
                description = "Lido pela biblioteca metadata-extractor (diretório ${dir.name}).",
                privacy = privacy, removal = RemovalPolicy.NOT_SUPPORTED, details = dir.name,
            )
        }
    }

    private fun standardOf(dir: String) = when {
        dir.startsWith("GPS") -> MetadataStandard.GPS
        dir.startsWith("Exif") -> MetadataStandard.EXIF
        dir.startsWith("IPTC") -> MetadataStandard.IPTC
        dir.startsWith("XMP") -> MetadataStandard.XMP
        dir.startsWith("ICC") -> MetadataStandard.ICC
        else -> MetadataStandard.CONTAINER
    }

    private fun libraryDimensions(md: Metadata): Pair<Int?, Int?> {
        for (dir in md.directories) {
            val w = dir.tags.firstOrNull { it.tagName.equals("Image Width", true) }?.let { runCatching { dir.getInt(it.tagType) }.getOrNull() }
            val h = dir.tags.firstOrNull { it.tagName.equals("Image Height", true) }?.let { runCatching { dir.getInt(it.tagType) }.getOrNull() }
            if (w != null && h != null) return w to h
        }
        return null to null
    }

    private fun fileEntries(info: ImageFileInfo): List<MetadataEntry> {
        fun e(key: String, name: String, value: String, desc: String) =
            MetadataEntry("FILE:$key", name, value, MetadataStandard.FILE, MetadataCategory.FILE, desc, PrivacyLevel.NONE)
        return listOfNotNull(
            e("name", "Nome do arquivo", info.displayName, "Nome do arquivo selecionado. O nome também pode conter informações pessoais."),
            e("format", "Formato real", info.format.displayName, "Determinado pela assinatura binária do arquivo, não pela extensão."),
            e("size", "Tamanho", humanSize(info.sizeBytes), "Tamanho do arquivo em disco."),
            e("dimensions", "Dimensões", info.resolutionLabel, "Largura × altura em pixels."),
            info.hasAlpha?.let { e("alpha", "Transparência", if (it) "Sim" else "Não", "Indica se a imagem possui canal alfa.") },
            info.isAnimated?.let { e("animated", "Animação", if (it) "Sim" else "Não", "Indica se o arquivo possui vários quadros.") },
            info.bitDepth?.let { e("depth", "Profundidade de bits", "$it bits por canal", "Precisão de cor por canal.") },
        )
    }

    private fun provenance(bytes: ByteArray, format: ImageFormat, entries: List<MetadataEntry>, blocks: MetadataBlocks): ProvenanceReport {
        val base = try { c2pa.inspect(bytes, format) } catch (e: Exception) {
            ProvenanceReport(ProvenanceVerdict.INCONCLUSIVE, false, 0, emptyList(), emptyList(), emptyList(),
                listOf("Falha ao inspecionar C2PA: ${e.message}"), ProvenanceReport.STANDARD_LIMITATIONS)
        }
        val declarations = base.aiDeclarations.toMutableList()
        val tools = base.declaredTools.toMutableList()
        val notes = base.notes.toMutableList()
        var remoteReference = false
        for (e in entries) {
            when {
                e.key == "XMP:Iptc4xmpExt:DigitalSourceType" -> {
                    val key = AiDeclarationDetector.digitalSourceTypeKey(e.value)
                    val label = key?.let { AiDeclarationDetector.AI_DIGITAL_SOURCE_TYPES[it] } ?: e.value
                    declarations += AiDeclaration("XMP (IPTC DigitalSourceType)", label, signed = false)
                }
                e.key.startsWith("XMP:Iptc4xmpExt:AI") -> declarations += AiDeclaration("XMP (IPTC)", "${e.name}: ${e.value}", signed = false)
                e.key == "XMP:dcterms:provenance" -> { remoteReference = true; notes += "Referência a manifesto de procedência: ${e.value}" }
                e.key in TOOL_KEYS -> {
                    tools += e.value
                    AiDeclarationDetector.detect(e.value)?.let { m -> declarations += AiDeclaration(e.name, "${m.reason}: ${e.value}", signed = false) }
                }
                e.removal == RemovalPolicy.PROTECTED_PROVENANCE && e.category == MetadataCategory.PROVENANCE && e.key !in XMP_PROVENANCE_HANDLED ->
                    declarations += AiDeclaration(e.name, e.value.take(300), signed = false)
            }
        }
        if (!base.c2paPresent && blocks.xmp.any { String(it, Charsets.UTF_8).contains("c2pa", ignoreCase = true) } && !remoteReference) {
            notes += "O XMP menciona C2PA, mas nenhum manifesto embutido foi encontrado."
            remoteReference = true
        }
        val verdict = when {
            base.c2paPresent -> base.verdict
            declarations.isNotEmpty() -> ProvenanceVerdict.DECLARED_ORIGIN
            remoteReference -> ProvenanceVerdict.INCONCLUSIVE
            else -> base.verdict
        }
        if (remoteReference && !base.c2paPresent) {
            notes += "Manifestos remotos não são baixados: o CleanShot funciona totalmente offline."
        }
        return base.copy(verdict = verdict, aiDeclarations = declarations.distinct(), declaredTools = tools.distinct(), notes = notes.distinct())
    }

    private fun provenanceEntries(p: ProvenanceReport, format: ImageFormat): List<MetadataEntry> {
        val out = mutableListOf<MetadataEntry>()
        fun e(key: String, name: String, value: String, desc: String, status: FindingStatus = FindingStatus.FOUND) =
            MetadataEntry(key, name, value, MetadataStandard.C2PA, MetadataCategory.PROVENANCE, desc, PrivacyLevel.NONE, status, RemovalPolicy.PROTECTED_PROVENANCE)
        if (!p.c2paPresent) {
            val supported = format in setOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.HEIF, ImageFormat.AVIF)
            out += e("C2PA:store", "Credenciais de conteúdo (C2PA)", if (supported) "Não encontradas" else "Não suportado",
                if (supported) "Nenhum manifesto C2PA embutido. Isso não indica se a imagem foi ou não gerada por IA."
                else "A localização de manifestos C2PA não é suportada para ${format.displayName}.",
                if (supported) FindingStatus.NOT_FOUND else FindingStatus.UNSUPPORTED)
            return out
        }
        out += e("C2PA:store", "Credenciais de conteúdo (C2PA)", "${p.manifests.size} manifesto(s) · ${humanSize(p.manifestStoreBytes.toLong())}",
            "Manifesto de procedência assinado. Nunca é removido ou alterado pelo CleanShot.")
        p.activeManifest?.let { m ->
            m.claimGenerator?.let { out += e("C2PA:generator", "Gerador do manifesto", it, "Aplicativo que criou o manifesto ativo.") }
            m.signer?.let { out += e("C2PA:signer", "Assinante", it.subject, "Certificado que assinou o manifesto. Emitido por: ${it.issuer}") }
            for (c in m.checks) {
                out += e("C2PA:check:${c.name}", c.name, c.state.label, c.detail,
                    if (c.state == CheckState.NOT_CHECKED) FindingStatus.UNVERIFIABLE else FindingStatus.FOUND)
            }
        }
        return out
    }

    /** Fields the inspector always reports, marking which ones are absent or unsupported. */
    private fun expectedFields(found: List<MetadataEntry>, format: ImageFormat): List<MetadataEntry> {
        val keys = found.map { it.key }.toSet()
        val out = mutableListOf<MetadataEntry>()
        val exifSupported = format != ImageFormat.GIF && format != ImageFormat.BMP
        val iimSupported = format == ImageFormat.JPEG || format == ImageFormat.TIFF
        fun missing(key: String, name: String, std: MetadataStandard, cat: MetadataCategory, supported: Boolean, why: String = "") {
            if (key in keys) return
            if (!format.canClean && supported) return // analysis-only formats use library keys
            out += MetadataEntry(key, name, if (supported) "—" else "Não suportado", std, cat,
                if (supported) "Campo não presente neste arquivo." else "O formato ${format.displayName} não transporta este tipo de metadado. $why".trim(),
                PrivacyLevel.NONE, if (supported) FindingStatus.NOT_FOUND else FindingStatus.UNSUPPORTED)
        }
        missing("EXIF:IFD0:0x010F", "Fabricante", MetadataStandard.EXIF, MetadataCategory.DEVICE, exifSupported)
        missing("EXIF:IFD0:0x0110", "Modelo da câmera", MetadataStandard.EXIF, MetadataCategory.DEVICE, exifSupported)
        missing("EXIF:ExifIFD:0x9003", "Data de captura", MetadataStandard.EXIF, MetadataCategory.DATES, exifSupported)
        missing("EXIF:GPS:Coordinates", "Coordenadas GPS", MetadataStandard.GPS, MetadataCategory.LOCATION, exifSupported)
        missing("EXIF:IFD0:0x0112", "Orientação", MetadataStandard.EXIF, MetadataCategory.FILE, exifSupported)
        missing("EXIF:ExifIFD:0x8827", "ISO", MetadataStandard.EXIF, MetadataCategory.CAPTURE, exifSupported)
        missing("EXIF:ExifIFD:0x829D", "Abertura (f)", MetadataStandard.EXIF, MetadataCategory.CAPTURE, exifSupported)
        missing("EXIF:ExifIFD:0x829A", "Velocidade do obturador", MetadataStandard.EXIF, MetadataCategory.CAPTURE, exifSupported)
        missing("EXIF:ExifIFD:0x920A", "Distância focal", MetadataStandard.EXIF, MetadataCategory.CAPTURE, exifSupported)
        missing("EXIF:IFD0:0x0131", "Software", MetadataStandard.EXIF, MetadataCategory.SOFTWARE, exifSupported)
        missing("EXIF:ExifIFD:0x9286", "Comentário do usuário", MetadataStandard.EXIF, MetadataCategory.DESCRIPTION, exifSupported)
        val iimWhy = "Neste formato, dados IPTC são transportados via XMP."
        missing("IPTC:2:080", "Autor (IPTC)", MetadataStandard.IPTC, MetadataCategory.AUTHORSHIP, iimSupported, iimWhy)
        missing("IPTC:2:116", "Copyright (IPTC)", MetadataStandard.IPTC, MetadataCategory.AUTHORSHIP, iimSupported, iimWhy)
        missing("IPTC:2:120", "Descrição (IPTC)", MetadataStandard.IPTC, MetadataCategory.DESCRIPTION, iimSupported, iimWhy)
        missing("IPTC:2:025", "Palavras-chave (IPTC)", MetadataStandard.IPTC, MetadataCategory.DESCRIPTION, iimSupported, iimWhy)
        val xmpSupported = format != ImageFormat.BMP
        missing("XMP:dc:creator", "Criador (XMP)", MetadataStandard.XMP, MetadataCategory.AUTHORSHIP, xmpSupported)
        missing("XMP:xmp:CreatorTool", "Ferramenta de criação (XMP)", MetadataStandard.XMP, MetadataCategory.SOFTWARE, xmpSupported)
        missing("XMP:xmpMM:History", "Histórico de edição (XMP)", MetadataStandard.XMP, MetadataCategory.SOFTWARE, xmpSupported)
        missing("XMP:Iptc4xmpExt:DigitalSourceType", "Tipo de fonte digital (IPTC/XMP)", MetadataStandard.XMP, MetadataCategory.PROVENANCE, xmpSupported)
        return out
    }

    companion object {
        const val MAX_BYTES = 200L * 1024 * 1024
        private val TOOL_KEYS = setOf("XMP:xmp:CreatorTool", "EXIF:IFD0:0x0131", "EXIF:IFD0:0x000B", "PNG:Software", "XMP:tiff:Software")
        private val XMP_PROVENANCE_HANDLED = setOf("XMP:Iptc4xmpExt:DigitalSourceType", "XMP:dcterms:provenance")

        fun humanSize(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
