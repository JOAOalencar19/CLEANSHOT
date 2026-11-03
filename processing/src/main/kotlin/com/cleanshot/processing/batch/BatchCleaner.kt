package com.cleanshot.processing.batch

import com.cleanshot.domain.engine.PrivacyCleaner
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Sequential batch processing with per-item failure isolation and cooperative cancellation.
 * Platform independent: the Android WorkManager worker delegates to this class.
 */
class BatchCleaner(private val cleaner: PrivacyCleaner) {

    data class Input(val id: String, val displayName: String, val file: File)

    sealed interface ItemResult {
        val id: String
        data class Success(override val id: String, val output: File, val report: CleaningReport) : ItemResult
        data class Failure(override val id: String, val reason: String) : ItemResult
        data class Cancelled(override val id: String) : ItemResult
    }

    enum class Stage { STARTED, FINISHED }

    data class Progress(val index: Int, val total: Int, val id: String, val stage: Stage, val result: ItemResult?)

    /**
     * Processes [inputs]; [outputFor] chooses the private output file of each item.
     * When the coroutine is cancelled, the current item finishes or is discarded atomically and the
     * remaining ones are reported as [ItemResult.Cancelled]. The CancellationException is rethrown.
     */
    suspend fun run(
        inputs: List<Input>,
        options: CleaningOptions,
        outputFor: (Input) -> File,
        onProgress: suspend (Progress) -> Unit = {},
    ): List<ItemResult> {
        val results = mutableListOf<ItemResult>()
        for ((index, input) in inputs.withIndex()) {
            try {
                currentCoroutineContext().ensureActive()
            } catch (e: CancellationException) {
                inputs.drop(index).forEach { results += ItemResult.Cancelled(it.id) }
                throw e
            }
            onProgress(Progress(index, inputs.size, input.id, Stage.STARTED, null))
            val output = outputFor(input)
            val result = try {
                val report = cleaner.clean(input.file, output, input.displayName, options)
                ItemResult.Success(input.id, output, report)
            } catch (e: CancellationException) {
                output.delete(); throw e
            } catch (e: Exception) {
                output.delete()
                ItemResult.Failure(input.id, e.message ?: e.javaClass.simpleName)
            }
            results += result
            onProgress(Progress(index, inputs.size, input.id, Stage.FINISHED, result))
        }
        return results
    }
}
