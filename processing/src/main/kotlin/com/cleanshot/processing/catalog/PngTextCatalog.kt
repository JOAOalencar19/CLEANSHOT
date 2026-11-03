package com.cleanshot.processing.catalog

import com.cleanshot.domain.model.CleaningTarget as T
import com.cleanshot.domain.model.MetadataCategory as C
import com.cleanshot.domain.model.PrivacyLevel as P

/** PNG textual keywords (PNG spec §11.3.4.2 plus common de-facto keys). */
object PngTextCatalog {
    const val XMP_KEYWORD = "XML:com.adobe.xmp"

    private fun f(name: String, desc: String, c: C, p: P, t: T?) = FieldInfo(name, desc, c, p, t)

    private val KNOWN = mapOf(
        "title" to f("Título (PNG)", "Título da imagem.", C.DESCRIPTION, P.LOW, T.COMMENTS),
        "author" to f("Autor (PNG)", "Nome do autor.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "description" to f("Descrição (PNG)", "Descrição da imagem.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        "copyright" to f("Copyright (PNG)", "Aviso de direitos.", C.AUTHORSHIP, P.MEDIUM, T.COPYRIGHT),
        "creation time" to f("Data de criação (PNG)", "Data de criação.", C.DATES, P.MEDIUM, T.DATES),
        "software" to f("Software (PNG)", "Software que gerou o arquivo.", C.SOFTWARE, P.LOW, T.SOFTWARE),
        "disclaimer" to f("Aviso legal (PNG)", "Aviso legal.", C.AUTHORSHIP, P.LOW, T.COPYRIGHT),
        "warning" to f("Advertência (PNG)", "Advertência sobre o conteúdo.", C.DESCRIPTION, P.LOW, T.COMMENTS),
        "source" to f("Dispositivo de origem (PNG)", "Dispositivo usado para criar a imagem.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "comment" to f("Comentário (PNG)", "Comentário livre.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        "date:create" to f("Data de criação (ImageMagick)", "Data registrada pelo ImageMagick.", C.DATES, P.LOW, T.DATES),
        "date:modify" to f("Data de modificação (ImageMagick)", "Data registrada pelo ImageMagick.", C.DATES, P.LOW, T.DATES),
        "date:timestamp" to f("Carimbo de data (ImageMagick)", "Data registrada pelo ImageMagick.", C.DATES, P.LOW, T.DATES),
    )

    fun info(keyword: String): FieldInfo {
        val k = keyword.lowercase()
        KNOWN[k]?.let { return it }
        return when {
            k.startsWith("exif:gps") -> f("$keyword (texto)", "Geolocalização em texto.", C.LOCATION, P.HIGH, T.LOCATION)
            k == "exif:make" || k == "exif:model" -> f("$keyword (texto)", "Dispositivo.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS)
            k.startsWith("raw profile type") -> f("Perfil bruto: ${keyword.substringAfter("type ").trim()}", "Metadados (EXIF/IPTC/XMP) codificados em hexadecimal pelo ImageMagick. A edição seletiva deste bloco não é suportada.", C.OTHER, P.MEDIUM, null)
            else -> f("Texto PNG: $keyword", "Campo de texto não catalogado. Mantido.", C.OTHER, P.LOW, null)
        }
    }
}
