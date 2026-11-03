package com.cleanshot.processing

import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.ProvenanceVerdict
import com.cleanshot.domain.model.UnsafeOperationException
import com.cleanshot.processing.c2pa.C2paLocator
import com.cleanshot.processing.container.BmffFile
import com.cleanshot.processing.container.JpegFile
import com.cleanshot.processing.container.PngFile
import com.cleanshot.processing.container.WebpFile
import com.cleanshot.processing.format.DefaultImageFormatDetector
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

class PrivacyCleanerTest {

    /** Decodes with the JDK's own decoders: a check fully independent of CleanShot's parsers. */
    private fun pixels(bytes: ByteArray): IntArray {
        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: error("ImageIO could not decode")
        return img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
    }

    @Test fun jpegPixelsAreIdenticalAndGpsRemoved() {
        val c = Fixtures.clean("generated/photo_full.jpg")
        val original = c.input.readBytes(); val result = c.output.readBytes()
        assertArrayEquals("decoded pixels", pixels(original), pixels(result))
        assertEquals(Fixtures.sha256(JpegFile.parse(original).imageData), Fixtures.sha256(JpegFile.parse(result).imageData))
        assertFalse(c.after.foundEntries.any { it.category == MetadataCategory.LOCATION })
        assertFalse(c.after.has("EXIF:IFD0:0x013B"))
        assertFalse(c.after.has("IPTC:2:080"))
        assertFalse(c.after.has("XMP:dc:creator"))
        assertFalse(c.after.has("COM:0"))
        assertFalse(c.after.has("EXIF:IFD1:0x0201"))
        assertTrue(c.report.allChecksPassed)
        assertFalse(c.report.reencoded)
        assertTrue(c.report.removed.any { it.key == "EXIF:GPS:Coordinates" })
    }

    @Test fun renderingAndProvenanceArePreserved() {
        val c = Fixtures.clean("generated/photo_full.jpg")
        assertEquals("Girada 90° horário", c.after.found("EXIF:IFD0:0x0112")?.value)
        assertTrue(c.after.has("ICC:profile"))
        assertTrue(c.after.has("XMP:Iptc4xmpExt:DigitalSourceType"))
        assertTrue(c.after.has("EXIF:ExifIFD:0x8827")) // technical capture data are not personal
        assertEquals(ProvenanceVerdict.DECLARED_ORIGIN, c.after.provenance.verdict)
    }

    @Test fun originalFileIsNeverModified() {
        val input = Fixtures.file("generated/photo_full.jpg")
        val before = Fixtures.sha256(input.readBytes())
        val modified = input.lastModified()
        Fixtures.cleaner.clean(input, File(input.parentFile, "o.jpg"), input.name, CleaningOptions.MAXIMUM)
        assertEquals(before, Fixtures.sha256(input.readBytes()))
        assertEquals(modified, input.lastModified())
        try {
            Fixtures.cleaner.clean(input, input, input.name, CleaningOptions.MAXIMUM); fail()
        } catch (_: UnsafeOperationException) { }
        assertEquals(before, Fixtures.sha256(input.readBytes()))
    }

    @Test fun selectiveCleaningOnlyRemovesSelectedGroup() {
        val c = Fixtures.clean("generated/photo_full.jpg", CleaningOptions(setOf(CleaningTarget.LOCATION)))
        assertFalse(c.after.foundEntries.any { it.category == MetadataCategory.LOCATION })
        assertTrue(c.after.has("EXIF:IFD0:0x013B"))
        assertTrue(c.after.has("XMP:dc:creator"))
        assertTrue(c.after.has("EXIF:IFD1:0x0201"))
        assertTrue(c.after.has("EXIF:ExifIFD:0x927C"))
    }

    @Test fun makerNoteStaysAtOriginalOffsetWhenKept() {
        val c = Fixtures.clean("generated/photo_full.jpg", CleaningOptions(setOf(CleaningTarget.LOCATION, CleaningTarget.AUTHOR_IDENTITY)))
        assertFalse(c.report.warnings.any { it.contains("MakerNote") })
        assertEquals(Fixtures.analyze("generated/photo_full.jpg").found("EXIF:ExifIFD:0x927C")?.value, c.after.found("EXIF:ExifIFD:0x927C")?.value)
    }

    @Test fun pngPixelsAndTransparencyArePreserved() {
        val c = Fixtures.clean("generated/alpha_meta.png")
        val o = c.input.readBytes(); val r = c.output.readBytes()
        assertArrayEquals(pixels(o), pixels(r))
        val alpha = ImageIO.read(ByteArrayInputStream(r))
        assertTrue(alpha.colorModel.hasAlpha())
        assertEquals((alpha.getRGB(0, 10) ushr 24), 0)
        fun idat(b: ByteArray) = PngFile.parse(b).chunks.filter { it.type == "IDAT" }.flatMap { it.data.toList() }
        assertEquals(idat(o), idat(r))
        assertFalse(c.after.has("PNG:Author"))
        assertFalse(c.after.has("PNG:tIME"))
        assertTrue("AI generation parameters are protected", c.after.has("PNG:parameters"))
        assertTrue(c.after.has("PNG:Software").not())
    }

    @Test fun webpImageChunksAndAnimationPreserved() {
        for (f in listOf("generated/alpha_meta.webp", "generated/animated_meta.webp", "generated/lossy_meta.webp")) {
            val c = Fixtures.clean(f)
            val o = WebpFile.parse(c.input.readBytes()); val r = WebpFile.parse(c.output.readBytes())
            fun img(w: WebpFile) = w.chunks.filter { it.fourcc in WebpFile.IMAGE_CHUNKS }.map { it.fourcc + Fixtures.sha256(it.data) }
            assertEquals(f, img(o), img(r))
            assertEquals(f, o.isAnimated, r.isAnimated)
            assertEquals(f, o.hasAlpha(), r.hasAlpha())
            assertFalse(f, c.after.foundEntries.any { it.category == MetadataCategory.LOCATION })
            assertTrue(f, c.report.allChecksPassed)
        }
    }

    @Test fun heifAndAvifAreRewrittenInPlaceWithoutConversion() {
        for (f in listOf("generated/meta.heic", "generated/alpha_meta.avif")) {
            val c = Fixtures.clean(f)
            val o = c.input.readBytes(); val r = c.output.readBytes()
            assertEquals(f, o.size, r.size)
            val fmt = DefaultImageFormatDetector().detect(r, f)
            assertEquals(f, DefaultImageFormatDetector().detect(o, f), fmt)
            val allowed = BmffFile.parse(o).let { b -> (b.exifItems() + b.xmpItems()).flatMap { it.extents } }
            val changedOutside = o.indices.count { o[it] != r[it] && allowed.none { e -> it >= e.offset && it < e.offset + e.length } }
            assertEquals(f, 0, changedOutside)
            assertFalse(f, c.after.foundEntries.any { it.category == MetadataCategory.LOCATION })
            assertTrue(f, c.after.has("XMP:Iptc4xmpExt:DigitalSourceType"))
            assertEquals(f, Fixtures.analyze(f).found("EXIF:IFD0:0x0112")?.value, c.after.found("EXIF:IFD0:0x0112")?.value)
        }
    }

    @Test fun c2paManifestIsPreservedByteForByte() {
        for (f in listOf("generated/signed.jpg", "generated/signed.png", "generated/signed.webp", "generated/signed.heic", "generated/signed.avif")) {
            val c = Fixtures.clean(f)
            val fmt = DefaultImageFormatDetector().detect(c.input.readBytes(), f)
            val a = C2paLocator.locate(c.input.readBytes(), fmt) as C2paLocator.Result.Found
            val b = C2paLocator.locate(c.output.readBytes(), fmt) as C2paLocator.Result.Found
            assertArrayEquals(f, a.jumbf, b.jumbf)
            assertTrue(f, c.report.warnings.any { it.contains("C2PA") })
            assertTrue(f, c.after.provenance.c2paPresent)
        }
        // For data-hash bound formats the modification is (correctly) reported.
        assertEquals(ProvenanceVerdict.CREDENTIALS_INVALID, Fixtures.clean("generated/signed.jpg").after.provenance.verdict)
    }

    @Test fun aiParametersInExifAreProtected() {
        val c = Fixtures.clean("generated/ai_parameters.jpg")
        assertTrue(c.after.found("EXIF:ExifIFD:0x9286")!!.value.contains("Steps: 30"))
        assertFalse(c.after.has("EXIF:IFD0:0x013B"))
    }

    @Test fun planExplainsBeforeProcessing() {
        val report = Fixtures.analyze("generated/signed.jpg")
        val plan = Fixtures.cleaner.plan(report, CleaningOptions.DEFAULT)
        assertTrue(plan.canProceed)
        assertFalse(plan.requiresReencoding)
        assertTrue(plan.toRemove.any { it.key == "EXIF:GPS:Coordinates" })
        assertTrue(plan.protected.any { it.key == "C2PA:store" })
        assertTrue(plan.warnings.any { it.contains("C2PA") })
        val nothing = Fixtures.cleaner.plan(Fixtures.analyze("c2pa-rs/sample1.heic"), CleaningOptions.DEFAULT)
        assertFalse(nothing.canProceed)
    }
}
