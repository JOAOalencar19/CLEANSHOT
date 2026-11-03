package com.cleanshot.domain.model

/**
 * Groups of conventional metadata the user may choose to remove.
 * Provenance credentials (C2PA) and AI-origin declarations are never part of any target.
 */
enum class CleaningTarget(val title: String, val description: String, val defaultSelected: Boolean) {
    LOCATION("Localização (GPS)", "Coordenadas, altitude, direção e nomes de locais.", true),
    AUTHOR_IDENTITY("Autoria pessoal", "Nome do autor, contato, proprietário da câmera.", true),
    COPYRIGHT("Direitos autorais", "Avisos de copyright e termos de uso.", false),
    COMMENTS("Comentários e descrições", "Comentários, títulos, legendas e descrições.", true),
    KEYWORDS("Palavras-chave", "Tags e palavras-chave.", false),
    DEVICE_IDENTIFIERS("Identificadores do dispositivo", "Fabricante, modelo, números de série, MakerNote.", false),
    DATES("Datas e horários", "Datas de captura, digitalização e modificação.", false),
    SOFTWARE("Software convencional", "Nome e versão de softwares de edição/câmera (exceto ferramentas de IA declaradas).", false),
    EDIT_HISTORY("Histórico de edição XMP", "Histórico, IDs de documento e ajustes de revelação (exceto entradas que citam IA).", false),
    THUMBNAIL("Miniaturas embutidas", "Prévias antigas que podem mostrar a imagem antes de recortes.", true),
    IPTC_EDITORIAL("Dados editoriais IPTC", "Créditos, fonte, instruções, categorias e referências de transmissão.", false),
}

data class CleaningOptions(val targets: Set<CleaningTarget>) {
    val isEmpty: Boolean get() = targets.isEmpty()

    companion object {
        val DEFAULT = CleaningOptions(CleaningTarget.entries.filter { it.defaultSelected }.toSet())
        val MAXIMUM = CleaningOptions(CleaningTarget.entries.toSet())
    }
}

/** What will happen to each found field, computed before processing (confirmation screen). */
data class CleaningPlan(
    val format: ImageFormat,
    val canProceed: Boolean,
    val blockingReason: String?,
    val toRemove: List<MetadataEntry>,
    val toPreserve: List<MetadataEntry>,
    val protected: List<MetadataEntry>,
    val unsupported: List<MetadataEntry>,
    val strategy: String,
    val warnings: List<String>,
    val requiresReencoding: Boolean = false,
)

/** A concrete structural modification applied to the file. */
data class FileChange(val description: String)

data class PreservationCheck(
    val name: String,
    val passed: Boolean,
    val detail: String,
)

data class CleaningReport(
    val originalName: String,
    val format: ImageFormat,
    val originalSize: Long,
    val resultSize: Long,
    val found: List<MetadataEntry>,
    val removed: List<MetadataEntry>,
    val preserved: List<MetadataEntry>,
    val unsupported: List<MetadataEntry>,
    val changes: List<FileChange>,
    val preservationChecks: List<PreservationCheck>,
    val reencoded: Boolean,
    val warnings: List<String>,
) {
    val allChecksPassed: Boolean get() = preservationChecks.all { it.passed }
}

/** Thrown when the engine cannot guarantee a safe result; the operation is aborted. */
class UnsafeOperationException(message: String) : Exception(message)
