package com.cleanshot.processing

import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.RemovalPolicy
import com.cleanshot.processing.container.JpegFile
import com.cleanshot.processing.iptc.IptcProcessor
import com.cleanshot.processing.xmp.XmpProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class IptcXmpTest {
    private val r = Fixtures.analyze("generated/photo_full.jpg")

    @Test fun readsIptcWithUtf8() {
        assertEquals("Maria Fotógrafa", r.found("IPTC:2:080")?.value)
        assertEquals("(c) 2024 Maria", r.found("IPTC:2:116")?.value)
        assertEquals("família; aniversário", r.found("IPTC:2:025")?.value)
        assertEquals("Festa de aniversário na casa da avó", r.found("IPTC:2:120")?.value)
        assertEquals(MetadataCategory.LOCATION, r.found("IPTC:2:090")?.category)
    }

    @Test fun iptcCleaningUpdatesPhotoshopDigest() {
        val jpeg = JpegFile.parse(Fixtures.bytes("generated/photo_full.jpg"))
        val ps = jpeg.segments.first { it.isPhotoshop() }
        val irb = ps.payload.copyOfRange(JpegFile.PHOTOSHOP_HEADER.size, ps.payload.size)
        val p = IptcProcessor()
        val res = p.cleanIrb(irb, setOf(CleaningTarget.AUTHOR_IDENTITY))
        val blocks = p.parseIrb(res.payload!!)
        val iim = blocks.first { it.id == IptcProcessor.IPTC_ID }.data
        val keys = p.entries(iim).map { it.key }
        assertFalse("IPTC:2:080" in keys)
        assertFalse("IPTC:2:118" in keys)
        assertTrue("IPTC:2:116" in keys)
        assertArrayEquals(MessageDigest.getInstance("MD5").digest(iim), blocks.first { it.id == IptcProcessor.DIGEST_ID }.data)
        assertTrue("other resources kept", blocks.any { it.id == 0x03ED })
    }

    @Test fun readsXmpAndProtectsProvenance() {
        assertEquals("Maria Fotografa", r.found("XMP:dc:creator")?.value)
        assertEquals("Adobe Photoshop 25.0", r.found("XMP:xmp:CreatorTool")?.value)
        val dst = r.found("XMP:Iptc4xmpExt:DigitalSourceType")!!
        assertEquals(RemovalPolicy.PROTECTED_PROVENANCE, dst.removal)
        assertEquals(MetadataCategory.PROVENANCE, dst.category)
        assertTrue(r.found("XMP:xmpMM:History")!!.value.contains("softwareAgent=Adobe Photoshop 25.0"))
    }

    @Test fun xmpCleanKeepsProvenanceAndAiMentions() {
        val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/"
              xmlns:Iptc4xmpExt="http://iptc.org/std/Iptc4xmpExt/2008-02-29/" xmp:CreatorTool="Adobe Firefly Image 3">
              <dc:creator><rdf:Seq><rdf:li>Joao</rdf:li></rdf:Seq></dc:creator>
              <Iptc4xmpExt:DigitalSourceType>http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia</Iptc4xmpExt:DigitalSourceType>
            </rdf:Description></rdf:RDF></x:xmpmeta>""".toByteArray()
        val res = XmpProcessor().clean(xmp, setOf(CleaningTarget.AUTHOR_IDENTITY, CleaningTarget.SOFTWARE))
        assertEquals(listOf("XMP:dc:creator"), res.removedKeys)
        assertTrue("XMP:xmp:CreatorTool" in res.protectedKeys)
        val keys = XmpProcessor().properties(res.xmp!!).map { it.key }
        assertTrue("XMP:Iptc4xmpExt:DigitalSourceType" in keys)
        assertTrue("XMP:xmp:CreatorTool" in keys)
        assertFalse("XMP:dc:creator" in keys)
    }

    @Test fun inPlaceXmpHasExactLength() {
        val jpeg = JpegFile.parse(Fixtures.bytes("generated/photo_full.jpg"))
        val seg = jpeg.segments.first { it.isXmp() }
        val xmp = seg.payload.copyOfRange(JpegFile.XMP_HEADER.size, seg.payload.size)
        val res = XmpProcessor().clean(xmp, setOf(CleaningTarget.LOCATION), exactLength = xmp.size)
        assertEquals(xmp.size, res.xmp!!.size)
        val keys = XmpProcessor().properties(res.xmp!!).map { it.key }
        assertFalse(keys.any { it.contains("GPS") || it.contains("City") })
        assertNotNull(keys.firstOrNull { it == "XMP:dc:creator" })
    }

    @Test fun externalEntitiesAreNeverResolved() {
        val secret = java.io.File.createTempFile("secret", ".txt").apply { writeText("TOP-SECRET") }
        val xxe = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "${secret.toURI()}">]>
            <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:format>&e;</dc:format></rdf:Description></rdf:RDF></x:xmpmeta>""".toByteArray()
        val leaked = runCatching { XmpProcessor().properties(xxe) }.getOrNull()?.any { it.value.contains("TOP-SECRET") } ?: false
        assertFalse(leaked)
    }

    @Test fun malformedXmpIsRejected() {
        val out = ByteArrayOutputStream().apply { write("<x:xmpmeta><rdf:RDF".toByteArray()) }.toByteArray()
        try { XmpProcessor().properties(out); throw AssertionError("expected failure") } catch (e: Exception) {
            assertTrue(e is com.cleanshot.processing.util.MalformedImageException)
        }
    }
}
