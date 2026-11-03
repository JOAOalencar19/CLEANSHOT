package com.cleanshot.processing

import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.processing.batch.BatchCleaner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BatchCleanerTest {
    private val batch = BatchCleaner(Fixtures.cleaner)

    private fun inputs(vararg paths: String) = paths.mapIndexed { i, p -> BatchCleaner.Input("$i", p.substringAfterLast('/'), Fixtures.file(p)) }

    @Test fun processesQueueAndIsolatesFailures() = runBlocking {
        val items = inputs("generated/photo_full.jpg", "generated/truncated.jpg", "generated/alpha_meta.png", "generated/meta.heic")
        val progress = mutableListOf<BatchCleaner.Progress>()
        val results = batch.run(items, CleaningOptions.DEFAULT, { File(it.file.parentFile, "b_" + it.file.name) }) { progress += it }
        assertEquals(4, results.size)
        assertTrue(results[0] is BatchCleaner.ItemResult.Success)
        assertTrue(results[1] is BatchCleaner.ItemResult.Failure)
        assertTrue(results[2] is BatchCleaner.ItemResult.Success)
        assertTrue(results[3] is BatchCleaner.ItemResult.Success)
        assertFalse(File(items[1].file.parentFile, "b_truncated.jpg").exists())
        assertEquals(8, progress.size)
    }

    @Test fun cancellationStopsRemainingItems() = runBlocking {
        val items = inputs("generated/photo_full.jpg", "generated/alpha_meta.png", "generated/meta.heic", "generated/alpha_meta.webp")
        val firstDone = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val job = async(Dispatchers.Default) {
            batch.run(items, CleaningOptions.DEFAULT, { File(it.file.parentFile, "c_" + it.file.name) }) {
                if (it.stage == BatchCleaner.Stage.FINISHED && it.index == 0) { firstDone.complete(Unit); gate.await() }
            }
        }
        firstDone.await()
        job.cancel()
        gate.complete(Unit)
        try { job.await(); throw AssertionError("expected cancellation") } catch (_: CancellationException) { }
        job.cancelAndJoin()
        assertTrue(File(items[0].file.parentFile, "c_photo_full.jpg").exists())
        assertFalse(File(items[2].file.parentFile, "c_meta.heic").exists())
        assertFalse(File(items[3].file.parentFile, "c_alpha_meta.webp").exists())
    }
}
