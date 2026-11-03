package com.cleanshot.domain.engine

import com.cleanshot.domain.model.AnalysisReport
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningPlan
import com.cleanshot.domain.model.CleaningReport
import com.cleanshot.domain.model.ImageFormat
import com.cleanshot.domain.model.PreservationCheck
import com.cleanshot.domain.model.ProvenanceReport
import java.io.File

/** Identifies the real container format from magic bytes (never trusts the extension alone). */
interface ImageFormatDetector {
    fun detect(header: ByteArray, fileName: String? = null): ImageFormat
}

/** Reads every metadata family of a local, private copy of an image. */
interface MetadataAnalyzer {
    fun analyze(file: File, displayName: String): AnalysisReport
}

/** Locates and inspects C2PA / Content Credentials manifests. Never modifies data. */
interface C2paInspector {
    fun inspect(bytes: ByteArray, format: ImageFormat): ProvenanceReport
}

/** Verifies that a processed file kept the original image data and rendering attributes. */
interface ImagePreservationEngine {
    fun verify(original: ByteArray, processed: ByteArray, format: ImageFormat): List<PreservationCheck>
}

/**
 * Removes conventional metadata. Implementations must:
 * - never touch [input];
 * - never re-encode pixels;
 * - preserve C2PA manifests and AI-origin declarations;
 * - throw [com.cleanshot.domain.model.UnsafeOperationException] when a safe result cannot be guaranteed.
 */
interface PrivacyCleaner {
    fun plan(report: AnalysisReport, options: CleaningOptions): CleaningPlan
    fun clean(input: File, output: File, displayName: String, options: CleaningOptions): CleaningReport
}
