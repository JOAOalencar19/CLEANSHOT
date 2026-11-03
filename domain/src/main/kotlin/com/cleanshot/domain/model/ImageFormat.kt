package com.cleanshot.domain.model

/**
 * Image container formats recognised by CleanShot.
 *
 * [cleaningSupport] describes what the processing engine can do with the format without
 * re-encoding pixels. Formats marked [CleaningSupport.ANALYSIS_ONLY] are inspected but never
 * modified: CleanShot refuses to convert them silently.
 */
enum class ImageFormat(
    val displayName: String,
    val mimeType: String,
    val extensions: List<String>,
    val cleaningSupport: CleaningSupport,
) {
    JPEG("JPEG", "image/jpeg", listOf("jpg", "jpeg", "jpe", "jfif"), CleaningSupport.LOSSLESS_REWRITE),
    PNG("PNG", "image/png", listOf("png"), CleaningSupport.LOSSLESS_REWRITE),
    WEBP("WebP", "image/webp", listOf("webp"), CleaningSupport.LOSSLESS_REWRITE),
    HEIF("HEIF/HEIC", "image/heif", listOf("heic", "heif", "hif"), CleaningSupport.IN_PLACE_ONLY),
    AVIF("AVIF", "image/avif", listOf("avif"), CleaningSupport.IN_PLACE_ONLY),
    GIF("GIF", "image/gif", listOf("gif"), CleaningSupport.ANALYSIS_ONLY),
    TIFF("TIFF", "image/tiff", listOf("tif", "tiff"), CleaningSupport.ANALYSIS_ONLY),
    DNG("DNG (RAW)", "image/x-adobe-dng", listOf("dng"), CleaningSupport.ANALYSIS_ONLY),
    BMP("BMP", "image/bmp", listOf("bmp"), CleaningSupport.ANALYSIS_ONLY),
    UNKNOWN("Desconhecido", "application/octet-stream", emptyList(), CleaningSupport.NONE);

    val canClean: Boolean
        get() = cleaningSupport == CleaningSupport.LOSSLESS_REWRITE ||
            cleaningSupport == CleaningSupport.IN_PLACE_ONLY

    companion object {
        fun fromMime(mime: String?): ImageFormat? = when (mime?.lowercase()) {
            null -> null
            "image/heic", "image/heic-sequence", "image/heif-sequence" -> HEIF
            "image/jpg" -> JPEG
            else -> entries.firstOrNull { it.mimeType == mime.lowercase() }
        }
    }
}

enum class CleaningSupport {
    /** Container is rebuilt chunk by chunk; image-data chunks are copied byte for byte. */
    LOSSLESS_REWRITE,

    /** Metadata items are rewritten in place with identical length; every other byte is kept. */
    IN_PLACE_ONLY,

    /** Metadata is analysed, but the file is never modified. */
    ANALYSIS_ONLY,

    NONE,
}
