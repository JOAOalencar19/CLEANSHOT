package com.cleanshot.processing

import com.cleanshot.domain.model.CheckState
import com.cleanshot.domain.model.ProvenanceVerdict
import com.cleanshot.processing.c2pa.DefaultC2paInspector
import com.cleanshot.processing.format.DefaultImageFormatDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected results come from the official c2pa-python 0.38.1 SDK
 * (see fixtures/c2pa-rs/NOTICE.md). Trust of the test certificate is never asserted.
 */
class C2paInspectorTest {
    private val inspector = DefaultC2paInspector()
    private fun inspect(path: String) = Fixtures.bytes(path).let { inspector.inspect(it, DefaultImageFormatDetector().detect(it, path)) }
    private fun check(path: String, name: String) = inspect(path).activeManifest!!.checks.first { it.name == name }.state

    @Test fun validManifestsMatchOfficialSdk() {
        for (f in listOf("c2pa-rs/C.jpg", "c2pa-rs/CA.jpg", "c2pa-rs/boxhash.jpg")) {
            val r = inspect(f)
            assertEquals(f, ProvenanceVerdict.CREDENTIALS_VERIFIED, r.verdict)
            assertEquals(CheckState.NOT_CHECKED, r.activeManifest!!.checks.first { it.name == "Confiança no emissor" }.state)
        }
    }

    @Test fun tamperedSignatureIsDetected() {
        assertEquals(ProvenanceVerdict.CREDENTIALS_INVALID, inspect("c2pa-rs/E-sig-CA.jpg").verdict)
        assertEquals(CheckState.FAILED, check("c2pa-rs/E-sig-CA.jpg", DefaultC2paInspector.SIGNATURE))
        assertEquals(CheckState.PASSED, check("c2pa-rs/E-sig-CA.jpg", DefaultC2paInspector.HARD_BINDING))
    }

    @Test fun tamperedContentIsDetected() {
        assertEquals(ProvenanceVerdict.CREDENTIALS_INVALID, inspect("c2pa-rs/XCA.jpg").verdict)
        assertEquals(CheckState.FAILED, check("c2pa-rs/XCA.jpg", DefaultC2paInspector.HARD_BINDING))
        assertEquals(CheckState.PASSED, check("c2pa-rs/XCA.jpg", DefaultC2paInspector.SIGNATURE))
    }

    @Test fun readsClaimDetails() {
        val m = inspect("c2pa-rs/CA.jpg").activeManifest!!
        assertTrue(m.claimGenerator!!.contains("make_test_images"))
        assertTrue(m.ingredients.any { it.contains("A.jpg") })
        assertTrue(m.signer!!.subject.contains("C2PA Signer"))
        assertEquals("PS256", m.signer!!.algorithm)
    }

    @Test fun locatesManifestsInEveryContainer() {
        for (f in listOf("generated/signed.jpg", "generated/signed.png", "generated/signed.webp")) {
            val r = inspect(f)
            assertEquals(f, ProvenanceVerdict.CREDENTIALS_VERIFIED, r.verdict)
            assertTrue(f, r.aiDeclarations.any { it.signed && it.statement.contains("IA generativa") })
        }
        for (f in listOf("generated/signed.heic", "generated/signed.avif")) {
            val r = inspect(f)
            // Signature verified; BMFF hash binding is not implemented and therefore not claimed.
            assertEquals(f, ProvenanceVerdict.CREDENTIALS_FOUND, r.verdict)
            assertEquals(CheckState.PASSED, r.activeManifest!!.checks.first { it.name == DefaultC2paInspector.SIGNATURE }.state)
            assertEquals(CheckState.NOT_CHECKED, r.activeManifest!!.checks.first { it.name == DefaultC2paInspector.HARD_BINDING }.state)
        }
    }

    @Test fun absenceNeverClaimsAuthenticity() {
        val r = Fixtures.analyze("c2pa-rs/sample1.png").provenance
        assertEquals(ProvenanceVerdict.NO_CREDENTIALS, r.verdict)
        assertFalse(r.c2paPresent)
        assertTrue(r.verdict.explanation.contains("NÃO indica"))
        assertTrue(r.limitations.any { it.contains("pixels") })
    }

    @Test fun remoteManifestReferenceIsInconclusive() {
        val r = Fixtures.analyze("c2pa-rs/cloud.jpg").provenance
        assertEquals(ProvenanceVerdict.INCONCLUSIVE, r.verdict)
    }

    @Test fun unsignedDeclarationsAreDeclaredOrigin() {
        val r = Fixtures.analyze("generated/photo_full.jpg").provenance
        assertEquals(ProvenanceVerdict.DECLARED_ORIGIN, r.verdict)
        assertTrue(r.aiDeclarations.all { !it.signed })
        val sd = Fixtures.analyze("generated/ai_parameters.jpg").provenance
        assertEquals(ProvenanceVerdict.DECLARED_ORIGIN, sd.verdict)
    }
}
