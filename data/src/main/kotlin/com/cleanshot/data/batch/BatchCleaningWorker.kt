package com.cleanshot.data.batch

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cleanshot.domain.engine.PrivacyCleaner
import com.cleanshot.domain.model.BatchItemState
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.OperationOutcome
import com.cleanshot.domain.model.OperationType
import com.cleanshot.domain.repository.DecodeVerifier
import com.cleanshot.domain.repository.ExportManager
import com.cleanshot.domain.repository.HistoryRepository
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.repository.WorkspaceRepository
import com.cleanshot.domain.util.OutputNaming
import com.cleanshot.domain.engine.ImageFormatDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Processes the persisted batch request one image at a time. Each image is copied privately,
 * cleaned (with the same verification as single-image cleaning), written to the chosen folder
 * and its private copies are deleted. A failure only affects its own item.
 */
@HiltWorker
class BatchCleaningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val store: BatchStateStore,
    private val workspace: WorkspaceRepository,
    private val cleaner: PrivacyCleaner,
    private val verifier: DecodeVerifier,
    private val exporter: ExportManager,
    private val detector: ImageFormatDetector,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val request = BatchRequestStore.read(applicationContext) ?: return Result.failure()
        store.ensureItems(request.items)
        val options = CleaningOptions(request.targets)
        val userSettings = settings.settings.first()
        val keepHistory = userSettings.keepHistory
        try {
            for ((index, ref) in request.items.withIndex()) {
                if (index in request.done) continue
                store.item(index) { it.copy(state = BatchItemState.RUNNING, progress = 0.05f) }
                var copy: File? = null
                var output: File? = null
                try {
                    copy = workspace.importCopy(ref)
                    store.item(index) { it.copy(progress = 0.3f) }
                    val format = detector.detect(copy.inputStream().use { s -> ByteArray(64).also { b -> s.read(b) } }, ref.displayName)
                    val name = OutputNaming.cleanedName(ref.displayName, userSettings.fileNameSuffix, format)
                    output = workspace.newOutputFile(name)
                    val report = cleaner.clean(copy, output, ref.displayName, options)
                    verifier.verify(copy, output)?.let { throw IllegalStateException(it) }
                    store.item(index) { it.copy(progress = 0.75f) }
                    val saved = exporter.saveToFolder(output.absolutePath, request.treeUri, name, format.mimeType)
                    BatchRequestStore.markDone(applicationContext, index)
                    store.item(index) {
                        it.copy(state = BatchItemState.SUCCEEDED, progress = 1f, removedCount = report.removed.size,
                            sizeAfter = report.resultSize, outputUri = saved.uri, message = saved.displayName)
                    }
                    if (keepHistory) history.add(HistoryRecord(fileName = ref.displayName, timestampMillis = System.currentTimeMillis(),
                        operation = OperationType.BATCH_CLEANING, originalFormat = format.displayName, resultFormat = format.displayName,
                        sizeBefore = report.originalSize, sizeAfter = report.resultSize, outcome = OperationOutcome.SUCCESS, removedCount = report.removed.size))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val nothingToDo = e.message?.contains("Nenhum dos metadados") == true
                    store.item(index) {
                        it.copy(state = if (nothingToDo) BatchItemState.SKIPPED else BatchItemState.FAILED, progress = 1f, message = e.message ?: "Erro desconhecido")
                    }
                    BatchRequestStore.markDone(applicationContext, index)
                    if (keepHistory && !nothingToDo) history.add(HistoryRecord(fileName = ref.displayName, timestampMillis = System.currentTimeMillis(),
                        operation = OperationType.BATCH_CLEANING, originalFormat = ref.mimeType ?: "?", resultFormat = null,
                        sizeBefore = ref.sizeBytes, sizeAfter = null, outcome = OperationOutcome.FAILED, message = e.message?.take(200)))
                } finally {
                    copy?.let { workspace.release(it) }
                    output?.let { workspace.release(it) }
                }
            }
            store.finish(cancelled = false)
            BatchRequestStore.delete(applicationContext)
            return Result.success()
        } catch (e: CancellationException) {
            store.finish(cancelled = true)
            BatchRequestStore.delete(applicationContext)
            throw e
        }
    }
}
