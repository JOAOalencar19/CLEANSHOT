package com.cleanshot.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cleanshot.data.export.AndroidDecodeVerifier
import com.cleanshot.data.export.AndroidExportManager
import com.cleanshot.data.workspace.PrivateWorkspace
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.processing.analyzer.DefaultMetadataAnalyzer
import com.cleanshot.processing.cleaner.DefaultPrivacyCleaner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the processing engine on the Android runtime (Android's own XML, crypto and image
 * decoders) with real fixture files.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceProcessingTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun fixture(name: String): File = File(ctx.cacheDir, "t_$name").apply { writeBytes(assets.open(name).use { it.readBytes() }) }

    @Test fun cleansEveryFormatAndPlatformDecoderAgrees() {
        val cleaner = DefaultPrivacyCleaner()
        for (name in listOf("photo_full.jpg", "alpha_meta.png", "alpha_meta.webp", "meta.heic")) {
            val input = fixture(name)
            val output = File(ctx.cacheDir, "out_$name")
            val report = cleaner.clean(input, output, name, CleaningOptions.MAXIMUM)
            assertTrue(name, report.allChecksPassed)
            assertFalse(name, report.reencoded)
            assertNull(name, AndroidDecodeVerifier().verify(input, output))
            val after = DefaultMetadataAnalyzer().analyze(output, name)
            assertFalse(name, after.foundEntries.any { it.category == MetadataCategory.LOCATION })
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.absolutePath, o)
            assertEquals(name, report.preservationChecks.isNotEmpty(), o.outWidth > 0)
        }
    }

    @Test fun c2paIsVerifiedWithAndroidCrypto() {
        val r = DefaultMetadataAnalyzer().analyze(fixture("signed.jpg"), "signed.jpg")
        assertEquals(com.cleanshot.domain.model.ProvenanceVerdict.CREDENTIALS_VERIFIED, r.provenance.verdict)
    }

    @Test fun exportCopiesExactBytesAndShareUsesFileProvider() = runTest {
        val src = fixture("photo_full.jpg")
        val exporter = AndroidExportManager(ctx, PrivateWorkspace(ctx))
        val dest = File(ctx.cacheDir, "exported.jpg").apply { delete(); createNewFile() }
        var last = 0f
        val result = exporter.saveTo(src.absolutePath, Uri.fromFile(dest).toString()) { last = it }
        assertArrayEquals(src.readBytes(), dest.readBytes())
        assertEquals(1f, last)
        assertEquals(src.length(), result.sizeBytes)
        val share = Uri.parse(exporter.prepareShare(src.absolutePath, "foto_clean.jpg"))
        assertEquals("content", share.scheme)
        val shared = ctx.contentResolver.openInputStream(share)!!.use { it.readBytes() }
        assertArrayEquals(src.readBytes(), shared)
    }
}
