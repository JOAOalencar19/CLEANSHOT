package com.cleanshot.domain.model

/** Metadata family a field belongs to. */
enum class MetadataStandard(val label: String) {
    FILE("Arquivo"),
    CONTAINER("Estrutura do arquivo"),
    EXIF("EXIF"),
    GPS("EXIF GPS"),
    MAKERNOTE("MakerNote"),
    IPTC("IPTC"),
    XMP("XMP"),
    ICC("Perfil ICC"),
    PNG_TEXT("Texto PNG"),
    JPEG_COMMENT("Comentário JPEG"),
    C2PA("C2PA"),
}

/** Thematic grouping used by the inspector UI (expandable sections). */
enum class MetadataCategory(val title: String, val order: Int) {
    FILE("Arquivo e imagem", 0),
    LOCATION("Localização", 1),
    AUTHORSHIP("Autoria e direitos", 2),
    DEVICE("Câmera e dispositivo", 3),
    CAPTURE("Parâmetros de captura", 4),
    DATES("Datas", 5),
    DESCRIPTION("Descrição e comentários", 6),
    SOFTWARE("Software e edição", 7),
    PROVENANCE("Procedência e IA", 8),
    COLOR("Cor e renderização", 9),
    OTHER("Outros", 10),
}

/** How sensitive a field is for the user's privacy. */
enum class PrivacyLevel(val label: String) {
    NONE("Sem risco"),
    LOW("Baixo"),
    MEDIUM("Médio"),
    HIGH("Alto"),
}

/** Distinguishes the four states required by the inspector. */
enum class FindingStatus(val label: String) {
    FOUND("Encontrado"),
    NOT_FOUND("Não encontrado"),
    UNSUPPORTED("Não suportado neste formato"),
    UNVERIFIABLE("Não pôde ser verificado"),
}

/** Whether CleanShot is allowed / able to remove the field. */
enum class RemovalPolicy(val label: String) {
    REMOVABLE("Pode ser removido"),
    PROTECTED_PROVENANCE("Protegido: procedência / declaração de IA"),
    PROTECTED_RENDERING("Protegido: necessário para exibir a imagem corretamente"),
    NOT_SUPPORTED("Remoção não suportada"),
    NOT_APPLICABLE("Informativo"),
}

/**
 * One inspected metadata field.
 *
 * @param key stable identifier (`EXIF:GPSLatitude`, `XMP:dc:creator`...), used to diff the
 *            analysis of an original file against the processed copy.
 */
data class MetadataEntry(
    val key: String,
    val name: String,
    val value: String,
    val standard: MetadataStandard,
    val category: MetadataCategory,
    val description: String,
    val privacy: PrivacyLevel,
    val status: FindingStatus = FindingStatus.FOUND,
    val removal: RemovalPolicy = RemovalPolicy.NOT_APPLICABLE,
    val cleaningTarget: CleaningTarget? = null,
    val details: String? = null,
)

data class MetadataSection(
    val category: MetadataCategory,
    val entries: List<MetadataEntry>,
) {
    val foundCount: Int get() = entries.count { it.status == FindingStatus.FOUND }
    val highestPrivacy: PrivacyLevel
        get() = entries.filter { it.status == FindingStatus.FOUND }.maxOfOrNull { it.privacy } ?: PrivacyLevel.NONE
}

data class ImageFileInfo(
    val displayName: String,
    val sizeBytes: Long,
    val format: ImageFormat,
    val width: Int?,
    val height: Int?,
    val hasAlpha: Boolean? = null,
    val isAnimated: Boolean? = null,
    val bitDepth: Int? = null,
    val colorProfile: String? = null,
) {
    val resolutionLabel: String get() = if (width != null && height != null) "$width × $height" else "—"
    val megapixels: Double? get() = if (width != null && height != null) width.toLong() * height / 1_000_000.0 else null
}

data class AnalysisReport(
    val file: ImageFileInfo,
    val sections: List<MetadataSection>,
    val provenance: ProvenanceReport,
    val warnings: List<String>,
) {
    val allEntries: List<MetadataEntry> get() = sections.flatMap { it.entries }
    val foundEntries: List<MetadataEntry> get() = allEntries.filter { it.status == FindingStatus.FOUND }
    val sensitiveCount: Int get() = foundEntries.count { it.privacy >= PrivacyLevel.MEDIUM }
    val hasLocation: Boolean get() = foundEntries.any { it.category == MetadataCategory.LOCATION }
}
