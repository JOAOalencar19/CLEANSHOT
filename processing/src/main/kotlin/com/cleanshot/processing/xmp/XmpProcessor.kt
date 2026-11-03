package com.cleanshot.processing.xmp

import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.domain.model.MetadataStandard
import com.cleanshot.domain.model.RemovalPolicy
import com.cleanshot.processing.ai.AiDeclarationDetector
import com.cleanshot.processing.util.MalformedImageException
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Parses and selectively edits XMP packets (RDF/XML) with a namespace-aware DOM.
 * Provenance properties, AI declarations and rendering structures are never removed.
 */
class XmpProcessor {

    /** A top-level XMP property (attribute or element of an rdf:Description). */
    data class XmpProperty(val namespace: String, val prefix: String, val local: String, val value: String) {
        val key: String get() = "XMP:$prefix:$local"
    }

    data class CleanResult(val xmp: ByteArray?, val removedKeys: List<String>, val protectedKeys: List<String>)

    fun properties(xmp: ByteArray): List<XmpProperty> {
        val doc = parse(xmp)
        return descriptions(doc).flatMap { d -> propertiesOf(d).map { it.second } }
    }

    fun entries(xmp: ByteArray): List<MetadataEntry> = properties(xmp)
        .groupBy { it.key }
        .map { (_, list) ->
            val p = list.first()
            val value = list.joinToString("; ") { it.value }
            val info = XmpCatalog.info(p.prefix, p.local)
            val protection = protection(p.prefix, p.local, value)
            MetadataEntry(
                key = p.key,
                name = info.name,
                value = value.take(2000),
                standard = MetadataStandard.XMP,
                category = if (protection == RemovalPolicy.PROTECTED_PROVENANCE) MetadataCategory.PROVENANCE else info.category,
                description = info.description + if (protection == RemovalPolicy.PROTECTED_PROVENANCE && info.category != MetadataCategory.PROVENANCE)
                    " Contém declaração relacionada a IA; será preservado." else "",
                privacy = info.privacy,
                removal = protection ?: if (info.target != null) RemovalPolicy.REMOVABLE else RemovalPolicy.NOT_APPLICABLE,
                cleaningTarget = if (protection == null) info.target else null,
                details = "${p.namespace} · ${p.local}",
            )
        }

    /** Returns null when the property can be removed, or the reason it is protected. */
    fun protection(prefix: String, local: String, value: String): RemovalPolicy? {
        val key = "$prefix:$local"
        if (key in XmpCatalog.PROVENANCE_PROPERTIES) return RemovalPolicy.PROTECTED_PROVENANCE
        val info = XmpCatalog.info(prefix, local)
        if (info.rendering) return RemovalPolicy.PROTECTED_RENDERING
        if (info.target in AI_SENSITIVE_TARGETS && AiDeclarationDetector.detect(value) != null) return RemovalPolicy.PROTECTED_PROVENANCE
        return null
    }

    /**
     * Removes the properties belonging to [targets].
     * @param exactLength when set (in-place rewrite), the output is padded with XMP whitespace to
     *        exactly this many bytes; fails if the result would not fit.
     */
    fun clean(xmp: ByteArray, targets: Set<CleaningTarget>, exactLength: Int? = null): CleanResult {
        val doc = parse(xmp)
        val removed = linkedSetOf<String>()
        val protected = linkedSetOf<String>()
        for (d in descriptions(doc)) {
            for ((node, p) in propertiesOf(d)) {
                val info = XmpCatalog.info(p.prefix, p.local)
                val target = info.target ?: continue
                if (target !in targets) continue
                if (protection(p.prefix, p.local, p.value) != null) { protected += p.key; continue }
                when (node) {
                    is Attr -> d.removeAttributeNode(node)
                    else -> d.removeChild(node)
                }
                removed += p.key
            }
        }
        if (removed.isEmpty()) return CleanResult(xmp, emptyList(), protected.toList())
        removeEmptyDescriptions(doc)
        val remaining = descriptions(doc).sumOf { propertiesOf(it).size }
        if (remaining == 0 && exactLength == null) return CleanResult(null, removed.toList(), protected.toList())
        return CleanResult(serialize(doc, xmp, exactLength), removed.toList(), protected.toList())
    }

    private fun removeEmptyDescriptions(doc: Document) {
        for (d in descriptions(doc)) {
            if (propertiesOf(d).isEmpty() && descriptions(doc).size > 1) d.parentNode.removeChild(d)
        }
    }

    internal fun parse(xmp: ByteArray): Document {
        val text = String(xmp, detectCharset(xmp)).trimEnd('\u0000')
        val start = text.indexOf('<').takeIf { it >= 0 } ?: throw MalformedImageException("XMP sem conteúdo XML")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            isXIncludeAware = false
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return try {
            factory.newDocumentBuilder().parse(InputSource(StringReader(text.substring(start))))
        } catch (e: Exception) {
            throw MalformedImageException("XMP inválido: ${e.message?.take(120)}")
        }
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try { setFeature(name, value) } catch (_: Exception) { /* not supported by this parser */ }
    }

    private fun descriptions(doc: Document): List<Element> {
        val list = doc.getElementsByTagNameNS(XmpCatalog.NS_RDF, "Description")
        return (0 until list.length).map { list.item(it) as Element }
            .filter { (it.parentNode as? Element)?.let { p -> p.namespaceURI == XmpCatalog.NS_RDF && p.localName == "RDF" } == true }
    }

    private fun propertiesOf(d: Element): List<Pair<Node, XmpProperty>> {
        val out = mutableListOf<Pair<Node, XmpProperty>>()
        val attrs = d.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i) as Attr
            val ns = a.namespaceURI ?: continue
            if (ns == XmpCatalog.NS_RDF || ns == "http://www.w3.org/2000/xmlns/" || ns == "http://www.w3.org/XML/1998/namespace") continue
            out += a to XmpProperty(ns, prefixFor(ns, a.prefix), a.localName, a.value)
        }
        var c = d.firstChild
        while (c != null) {
            if (c is Element && c.namespaceURI != null) {
                out += c to XmpProperty(c.namespaceURI, prefixFor(c.namespaceURI, c.prefix), c.localName, flatten(c))
            }
            c = c.nextSibling
        }
        return out
    }

    private fun prefixFor(ns: String, docPrefix: String?): String = XmpCatalog.PREFIXES[ns] ?: docPrefix ?: "ns"

    /** Compact textual rendering of a (possibly structured) property. */
    private fun flatten(e: Element): String {
        val parts = mutableListOf<String>()
        fun walk(n: Node, label: String?) {
            when (n) {
                is Element -> {
                    val attrs = n.attributes
                    for (i in 0 until attrs.length) {
                        val a = attrs.item(i) as Attr
                        if (a.namespaceURI == XmpCatalog.NS_RDF || a.namespaceURI == "http://www.w3.org/2000/xmlns/" || a.namespaceURI == "http://www.w3.org/XML/1998/namespace") continue
                        parts += "${a.localName}=${a.value}"
                    }
                    val isContainer = n.namespaceURI == XmpCatalog.NS_RDF
                    var c = n.firstChild
                    while (c != null) { walk(c, if (isContainer) label else n.localName); c = c.nextSibling }
                }
                else -> if (n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE) {
                    val t = n.nodeValue.trim()
                    if (t.isNotEmpty()) parts += if (label != null && label != e.localName) "$label=$t" else t
                }
            }
        }
        walk(e, null)
        return parts.joinToString("; ")
    }

    private fun serialize(doc: Document, original: ByteArray, exactLength: Int?): ByteArray {
        val root = doc.documentElement
        val tf = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        val sw = StringWriter()
        tf.transform(DOMSource(root), StreamResult(sw))
        val body = sw.toString()
        val originalText = String(original, detectCharset(original))
        val hasPacket = originalText.contains("<?xpacket")
        val header = if (hasPacket) "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" else ""
        val trailerWritable = if (hasPacket) "<?xpacket end=\"w\"?>" else ""
        val core = (header + body + "\n").toByteArray(Charsets.UTF_8)
        val trailer = trailerWritable.toByteArray(Charsets.UTF_8)
        if (exactLength == null) {
            // No extra padding: the cleaned copy should not grow.
            return core + trailer
        }
        val padLen = exactLength - core.size - trailer.size
        if (padLen < 0) throw MalformedImageException("XMP reescrito não cabe no espaço original")
        val padding = ByteArray(padLen) { if ((it + 1) % 100 == 0) '\n'.code.toByte() else ' '.code.toByte() }
        return core + padding + trailer
    }

    private fun detectCharset(b: ByteArray) = when {
        b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte() -> Charsets.UTF_16BE
        b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte() -> Charsets.UTF_16LE
        else -> Charsets.UTF_8
    }

    companion object {
        private val AI_SENSITIVE_TARGETS = setOf(CleaningTarget.SOFTWARE, CleaningTarget.EDIT_HISTORY, CleaningTarget.COMMENTS, CleaningTarget.KEYWORDS)
    }
}
