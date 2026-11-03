package com.cleanshot.processing

import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.processing.format.DefaultImageFormatDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDetectorTest {
    private val d = DefaultImageFormatDetector()

    @Test fun detectsRealFormatsFromSignature() {
        mapOf(
            "generated/photo_full.jpg" to ImageFormat.JPEG,
            "generated/alpha_meta.png" to ImageFormat.PNG,
            "generated/alpha_meta.webp" to ImageFormat.WEBP,
            "generated/animated_meta.webp" to ImageFormat.WEBP,
            "generated/meta.heic" to ImageFormat.HEIF,
            "c2pa-rs/sample1.heic" to ImageFormat.HEIF,
            "generated/alpha_meta.avif" to ImageFormat.AVIF,
            "c2pa-rs/sample1.avif" to ImageFormat.AVIF,
        ).forEach { (path, expected) -> assertEquals(path, expected, d.detect(Fixtures.bytes(path), path)) }
    }

    @Test fun extensionNeverOverridesSignature() {
        assertEquals(ImageFormat.PNG, d.detect(Fixtures.bytes("generated/alpha_meta.png"), "foto.jpg"))
    }

    @Test fun unknownAndTinyInputs() {
        assertEquals(ImageFormat.UNKNOWN, d.detect(Fixtures.bytes("generated/random.bin"), "x.jpg"))
        assertEquals(ImageFormat.UNKNOWN, d.detect(ByteArray(3), null))
    }
}
