package com.cleanshot.processing

import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.PrivacyLevel
import com.cleanshot.domain.model.RemovalPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifReadTest {
    private val r = Fixtures.analyze("generated/photo_full.jpg")

    private fun v(key: String) = r.found(key)?.value

    @Test fun readsCameraAndCaptureFields() {
        assertEquals("CleanCam", v("EXIF:IFD0:0x010F"))
        assertEquals("CC-1000 Pro", v("EXIF:IFD0:0x0110"))
        assertEquals("2024:05:01 10:20:30", v("EXIF:ExifIFD:0x9003"))
        assertEquals("200", v("EXIF:ExifIFD:0x8827"))
        assertEquals("f/1.8", v("EXIF:ExifIFD:0x829D"))
        assertEquals("1/120 s", v("EXIF:ExifIFD:0x829A"))
        assertEquals("4.2 mm", v("EXIF:ExifIFD:0x920A"))
        assertEquals("CleanCam Firmware 1.2.3", v("EXIF:IFD0:0x0131"))
        assertEquals("Lembranca da viagem", v("EXIF:ExifIFD:0x9286"))
        assertEquals("SN-0099887766", v("EXIF:ExifIFD:0xA431"))
    }

    @Test fun orientationIsProtected() {
        val o = r.found("EXIF:IFD0:0x0112")!!
        assertEquals("Girada 90° horário", o.value)
        assertEquals(RemovalPolicy.PROTECTED_RENDERING, o.removal)
    }

    @Test fun readsGpsAsHighPrivacyLocation() {
        val c = r.found("EXIF:GPS:Coordinates")!!
        assertEquals("-23.553428, -46.649106", c.value)
        assertEquals(MetadataCategory.LOCATION, c.category)
        assertEquals(PrivacyLevel.HIGH, c.privacy)
        assertEquals(CleaningTarget.LOCATION, c.cleaningTarget)
        assertEquals("760 m", v("EXIF:GPS:0x0006"))
        assertTrue(r.hasLocation)
    }

    @Test fun reportsMissingFieldsExplicitly() {
        val noGps = Fixtures.analyze("c2pa-rs/sample1.png")
        val gps = noGps.allEntries.first { it.key == "EXIF:GPS:Coordinates" }
        assertEquals(com.cleanshot.domain.model.FindingStatus.NOT_FOUND, gps.status)
        val iptc = noGps.allEntries.first { it.key == "IPTC:2:080" }
        assertEquals(com.cleanshot.domain.model.FindingStatus.UNSUPPORTED, iptc.status)
    }
}
