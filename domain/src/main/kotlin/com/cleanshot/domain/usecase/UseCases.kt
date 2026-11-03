package com.cleanshot.domain.usecase

import com.cleanshot.domain.engine.MetadataAnalyzer
import com.cleanshot.domain.engine.PrivacyCleaner
import com.cleanshot.domain.model.AnalysisReport
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningPlan
import com.cleanshot.domain.model.CleaningReport
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.ImageRef
import com.cleanshot.domain.model.OperationOutcome
import com.cleanshot.domain.model.OperationType
import com.cleanshot.domain.model.UnsafeOperationException
import com.cleanshot.domain.repository.DecodeVerifier
import com.cleanshot.domain.repository.HistoryRepository
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named

/** Result of analysing one image; [localCopy] is a private copy kept for the session. */
data class AnalyzedImage(val ref: ImageRef, val localCopy: File, val report: AnalysisReport)

data class CleanedImage(val source: AnalyzedImage, val output: File, val report: CleaningReport)

class AnalyzeImageUseCase @Inject constructor(
    private val workspace: WorkspaceRepository,
    private val analyzer: MetadataAnalyzer,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    @Named("io") private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(ref: ImageRef): AnalyzedImage = withContext(io) {
        val copy = workspace.importCopy(ref)
        try {
            val report = analyzer.analyze(copy, ref.displayName)
            record(HistoryRecord(fileName = ref.displayName, timestampMillis = System.currentTimeMillis(), operation = OperationType.ANALYSIS,
                originalFormat = report.file.format.displayName, resultFormat = null, sizeBefore = report.file.sizeBytes, sizeAfter = null,
                outcome = OperationOutcome.SUCCESS, removedCount = 0))
            AnalyzedImage(ref, copy, report)
        } catch (e: Exception) {
            workspace.release(copy)
            record(HistoryRecord(fileName = ref.displayName, timestampMillis = System.currentTimeMillis(), operation = OperationType.ANALYSIS,
                originalFormat = ref.mimeType ?: "?", resultFormat = null, sizeBefore = ref.sizeBytes, sizeAfter = null,
                outcome = OperationOutcome.FAILED, message = e.message?.take(200)))
            throw e
        }
    }

    private suspend fun record(r: HistoryRecord) {
        if (settings.settings.first().keepHistory) history.add(r)
    }
}

class PlanCleaningUseCase @Inject constructor(private val cleaner: PrivacyCleaner) {
    operator fun invoke(image: AnalyzedImage, options: CleaningOptions): CleaningPlan = cleaner.plan(image.report, options)
}

class CleanImageUseCase @Inject constructor(
    private val workspace: WorkspaceRepository,
    private val cleaner: PrivacyCleaner,
    private val decodeVerifier: DecodeVerifier,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    @Named("io") private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(image: AnalyzedImage, options: CleaningOptions, outputName: String): CleanedImage = withContext(io) {
        val output = workspace.newOutputFile(outputName)
        val keep = settings.settings.first().keepHistory
        try {
            val report = cleaner.clean(image.localCopy, output, image.ref.displayName, options)
            decodeVerifier.verify(image.localCopy, output)?.let { reason ->
                throw UnsafeOperationException("O decodificador do sistema não confirmou o resultado: $reason")
            }
            if (keep) history.add(HistoryRecord(fileName = image.ref.displayName, timestampMillis = System.currentTimeMillis(),
                operation = OperationType.CLEANING, originalFormat = report.format.displayName, resultFormat = report.format.displayName,
                sizeBefore = report.originalSize, sizeAfter = report.resultSize, outcome = OperationOutcome.SUCCESS, removedCount = report.removed.size))
            CleanedImage(image, output, report)
        } catch (e: Exception) {
            workspace.release(output)
            if (keep) history.add(HistoryRecord(fileName = image.ref.displayName, timestampMillis = System.currentTimeMillis(),
                operation = OperationType.CLEANING, originalFormat = image.report.file.format.displayName, resultFormat = null,
                sizeBefore = image.report.file.sizeBytes, sizeAfter = null, outcome = OperationOutcome.FAILED, message = e.message?.take(200)))
            throw e
        }
    }
}
