package com.cleanshot.processing.exif

import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.FindingStatus
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.domain.model.MetadataStandard
import com.cleanshot.domain.model.PrivacyLevel
import com.cleanshot.domain.model.RemovalPolicy
import com.cleanshot.processing.ai.AiDeclarationDetector
import com.cleanshot.processing.util.MalformedImageException

/**
 * Reads and selectively rewrites EXIF (TIFF) blocks.
 * Orientation, colour information and image-structure tags are never removed.
 */
class ExifProcessor {

    data class CleanResult(
        /** New TIFF bytes, or null when nothing remains worth keeping. */
        val tiff: ByteArray?,
        val removedKeys: List<String>,
        val changes: List<String>,
        val warnings: List<String>,
    )

    fun entries(tiff: ByteArray): List<MetadataEntry> {
        val block = TiffParser.parse(tiff)
        val fmt = ExifValueFormatter(block.littleEndian)
        val out = mutableListOf<MetadataEntry>()
        for ((ifd, list) in block.ifds) {
            if (ifd == IfdType.IFD1) continue
            for (e in list) {
                if (e.tag in TiffTags.POINTERS) continue
                val info = ExifCatalog.info(ifd, e.tag)
                val value = runCatching { fmt.format(ifd, e) }.getOrDefault("?")
                val ai = aiMatch(ifd, e, value)
                out += MetadataEntry(
                    key = key(ifd, e.tag),
                    name = info.name,
                    value = value.take(2000),
                    standard = if (ifd == IfdType.GPS) MetadataStandard.GPS else if (e.tag == TiffTags.MAKER_NOTE) MetadataStandard.MAKERNOTE else MetadataStandard.EXIF,
                    category = if (ai != null) MetadataCategory.PROVENANCE else info.category,
                    description = if (ai != null) info.description + " Contém declaração relacionada a IA (${ai.reason}); será preservado." else info.description,
                    privacy = info.privacy,
                    removal = when {
                        ai != null -> RemovalPolicy.PROTECTED_PROVENANCE
                        info.rendering -> RemovalPolicy.PROTECTED_RENDERING
                        info.target != null -> RemovalPolicy.REMOVABLE
                        else -> RemovalPolicy.NOT_APPLICABLE
                    },
                    cleaningTarget = if (ai == null) info.target else null,
                    details = "${ifd.label} · tag 0x%04X · tipo ${e.type} · ${e.count} valor(es)".format(e.tag),
                )
            }
        }
        coordinates(block, fmt)?.let { out += it }
        block.thumbnail?.let {
            out += MetadataEntry(
                key = key(IfdType.IFD1, TiffTags.JPEG_IF_OFFSET),
                name = "Miniatura EXIF",
                value = "%.1f KB".format(it.size / 1024.0),
                standard = MetadataStandard.EXIF,
                category = MetadataCategory.FILE,
                description = "Prévia embutida. Pode mostrar a imagem original antes de recortes ou edições.",
                privacy = PrivacyLevel.MEDIUM,
                removal = RemovalPolicy.REMOVABLE,
                cleaningTarget = CleaningTarget.THUMBNAIL,
            )
        }
        return out
    }

    /** Decimal coordinates (display only; never persisted). */
    private fun coordinates(block: TiffBlock, fmt: ExifValueFormatter): MetadataEntry? {
        val gps = block.entries(IfdType.GPS)
        fun n(tag: Int) = gps.firstOrNull { it.tag == tag }?.let { fmt.numbers(it) }
        fun s(tag: Int) = gps.firstOrNull { it.tag == tag }?.let { fmt.ascii(it) }
        val lat = n(2)?.takeIf { it.size >= 3 } ?: return null
        val lon = n(4)?.takeIf { it.size >= 3 } ?: return null
        var la = lat[0] + lat[1] / 60 + lat[2] / 3600
        var lo = lon[0] + lon[1] / 60 + lon[2] / 3600
        if (s(1).equals("S", true)) la = -la
        if (s(3).equals("W", true)) lo = -lo
        return MetadataEntry(
            key = "EXIF:GPS:Coordinates",
            name = "Coordenadas",
            value = "%.6f, %.6f".format(java.util.Locale.US, la, lo),
            standard = MetadataStandard.GPS,
            category = MetadataCategory.LOCATION,
            description = "Posição exata onde a foto foi tirada. Qualquer pessoa com o arquivo pode localizar o lugar em um mapa.",
            privacy = PrivacyLevel.HIGH,
            removal = RemovalPolicy.REMOVABLE,
            cleaningTarget = CleaningTarget.LOCATION,
        )
    }

    private fun aiMatch(ifd: IfdType, e: TiffEntry, value: String): AiDeclarationDetector.Match? {
        if (ifd !in setOf(IfdType.IFD0, IfdType.EXIF)) return null
        if (e.tag !in AI_SENSITIVE_TAGS) return null
        return AiDeclarationDetector.detect(value)
    }

    fun clean(tiff: ByteArray, targets: Set<CleaningTarget>): CleanResult {
        val block = TiffParser.parse(tiff)
        val fmt = ExifValueFormatter(block.littleEndian)
        val ifd0 = block.entries(IfdType.IFD0)
        if (ifd0.any { it.tag == TiffTags.SUB_IFDS || (it.type == 13 && it.tag !in TiffTags.POINTERS) }) {
            throw MalformedImageException("O bloco EXIF contém sub-IFDs não suportados; a reescrita não pode ser garantida")
        }
        val removed = mutableListOf<String>()
        val changes = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (ifd in listOf(IfdType.IFD0, IfdType.EXIF, IfdType.GPS, IfdType.INTEROP)) {
            val list = block.ifds[ifd] ?: continue
            val it = list.iterator()
            var count = 0
            while (it.hasNext()) {
                val e = it.next()
                if (e.tag in TiffTags.POINTERS) continue
                val info = ExifCatalog.info(ifd, e.tag)
                val target = info.target ?: continue
                if (info.rendering || target !in targets) continue
                val value = runCatching { fmt.format(ifd, e) }.getOrDefault("")
                if (aiMatch(ifd, e, value) != null) continue
                it.remove(); removed += key(ifd, e.tag); count++
            }
            if (count > 0) changes += "EXIF ${ifd.label}: $count campo(s) removido(s)"
        }
        if (CleaningTarget.LOCATION in targets && block.entries(IfdType.GPS).isEmpty() && removed.any { it.startsWith("EXIF:GPS:") }) {
            block.ifds.remove(IfdType.GPS)
            removed += "EXIF:GPS:Coordinates"
            changes += "EXIF: diretório GPS eliminado"
        }
        if (CleaningTarget.THUMBNAIL in targets && block.ifds.containsKey(IfdType.IFD1)) {
            if (block.thumbnail != null) removed += key(IfdType.IFD1, TiffTags.JPEG_IF_OFFSET)
            block.ifds.remove(IfdType.IFD1)
            block.thumbnail = null
            changes += "EXIF: miniatura (IFD1) removida"
        }
        if (removed.isEmpty()) return CleanResult(tiff, emptyList(), emptyList(), emptyList())

        val result = TiffWriter.write(block)
        if (result.makerNoteRelocated) {
            warnings += "A MakerNote precisou ser realocada; alguns softwares do fabricante podem não interpretá-la."
        }
        val meaningful = block.ifds.values.any { list -> list.any { it.tag !in TiffTags.POINTERS } }
        return CleanResult(if (meaningful) result.bytes else null, removed, changes, warnings)
    }

    companion object {
        fun key(ifd: IfdType, tag: Int) = "EXIF:${ifd.label}:0x%04X".format(tag)

        /** Free-text tags where generators sometimes write prompts or tool names. */
        private val AI_SENSITIVE_TAGS = setOf(0x000B, 0x010E, 0x0131, 0x9286, 0x9C9C, 0x9C9B, 0x9C9F, 0x013B, 0x8298)
    }
}
