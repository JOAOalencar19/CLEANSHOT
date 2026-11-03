package com.cleanshot.processing

import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.domain.util.OutputNaming
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputNamingTest {
    @Test fun keepsRealExtension() {
        assertEquals("IMG_1_clean.jpg", OutputNaming.cleanedName("IMG_1.jpg", "_clean", ImageFormat.JPEG))
        assertEquals("foto_clean.png", OutputNaming.cleanedName("foto.jpg", "_clean", ImageFormat.PNG))
        assertEquals("IMG_clean.heic", OutputNaming.cleanedName("IMG.HEIC", "_clean", ImageFormat.HEIF))
        assertEquals("a_b_clean.webp", OutputNaming.cleanedName("a/b.webp", "_clean", ImageFormat.WEBP))
        assertEquals("imagem_x.avif", OutputNaming.cleanedName(".avif", "_x", ImageFormat.AVIF))
    }
}
