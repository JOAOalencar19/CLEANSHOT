package com.cleanshot.processing.cleaner

import com.cleanshot.domain.model.ImageFormat

/** Builds safe output file names that keep the real format's extension. */
object OutputNaming {
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun cleanedName(originalName: String, suffix: String, format: ImageFormat): String {
        val base = originalName.substringBeforeLast('.', originalName).ifBlank { "imagem" }
        val originalExt = originalName.substringAfterLast('.', "").lowercase()
        val ext = if (originalExt in format.extensions) originalExt else format.extensions.firstOrNull() ?: "bin"
        val safeSuffix = suffix.replace(ILLEGAL, "")
        return sanitize("$base$safeSuffix") + ".$ext"
    }

    fun sanitize(name: String): String = name.replace(ILLEGAL, "_").trim().trim('.').take(120).ifBlank { "imagem" }
}
