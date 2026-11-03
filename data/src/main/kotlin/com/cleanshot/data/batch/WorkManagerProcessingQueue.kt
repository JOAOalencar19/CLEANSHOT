package com.cleanshot.data.batch

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cleanshot.domain.model.BatchProgress
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.ImageRef
import com.cleanshot.domain.repository.ProcessingQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** [ProcessingQueue] backed by a unique WorkManager job, so processing survives the UI. */
@Singleton
class WorkManagerProcessingQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BatchStateStore,
) : ProcessingQueue {

    override val progress: Flow<BatchProgress> = store.state

    override fun enqueue(images: List<ImageRef>, options: CleaningOptions, destinationTreeUri: String) {
        BatchRequestStore.write(context, BatchRequestStore.Request(images, options.targets, destinationTreeUri, emptySet()))
        store.start(images)
        val work = OneTimeWorkRequestBuilder<BatchCleaningWorker>().addTag(TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    override fun clearFinished() {
        if (!store.state.value.running) store.clear()
    }

    companion object {
        const val UNIQUE_NAME = "cleanshot-batch"
        const val TAG = "cleanshot-batch"
    }
}
