package com.cleanshot.processing

import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.UnsafeOperationException
import com.cleanshot.processing.util.MalformedImageException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class RobustnessTest {

    private fun expectMalformed(path: String) {
        try { Fixtures.analyze(path); fail("$path should be rejected") } catch (_: MalformedImageException) { }
    }

    @Test fun invalidFilesAreRejected() {
        expectMalformed("generated/random.bin")
        expectMalformed("generated/no_sos.jpg")
        expectMalformed("generated/bad_crc.png")
        try { Fixtures.analyzer.analyzeBytes(ByteArray(0), "vazio.jpg"); fail() } catch (_: Exception) { }
    }

    @Test fun truncatedJpegIsAnalysedButNeverCleaned() {
        val r = Fixtures.analyze("generated/truncated.jpg")
        assertTrue(r.warnings.any { it.contains("truncado") })
        val input = Fixtures.file("generated/truncated.jpg")
        val out = File(input.parentFile, "t_out.jpg")
        try { Fixtures.cleaner.clean(input, out, input.name, CleaningOptions.MAXIMUM); fail() } catch (_: UnsafeOperationException) { }
        assertFalse(out.exists())
    }

    @Test fun analysisOnlyFormatsAreNeverWritten() {
        val tiff = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "tiff", it)
        }.toByteArray()
        val r = Fixtures.analyzer.analyzeBytes(tiff, "x.tif")
        val plan = Fixtures.cleaner.plan(r, CleaningOptions.MAXIMUM)
        assertFalse(plan.canProceed)
        assertTrue(plan.blockingReason!!.contains("apenas para análise"))
    }

    @Test fun randomMutationsNeverCrashTheAnalyzer() {
        val base = Fixtures.bytes("generated/photo_full.jpg")
        val rnd = java.util.Random(42)
        repeat(200) {
            val b = base.copyOf()
            repeat(8) { b[rnd.nextInt(minOf(b.size, 8000))] = rnd.nextInt(256).toByte() }
            try { Fixtures.analyzer.analyzeBytes(b, "m.jpg") } catch (_: MalformedImageException) { }
        }
    }
}
