package com.cleanshot.domain.model

/** A user-selected image, referenced by an opaque URI string (content:// on Android). */
data class ImageRef(
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
)

data class ImportedImage(
    val ref: ImageRef,
    val format: ImageFormat,
    val width: Int?,
    val height: Int?,
)

enum class BatchItemState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, SKIPPED }

data class BatchItemProgress(
    val ref: ImageRef,
    val state: BatchItemState,
    val progress: Float,
    val message: String? = null,
    val removedCount: Int = 0,
    val sizeBefore: Long = ref.sizeBytes,
    val sizeAfter: Long? = null,
    val outputUri: String? = null,
)

data class BatchProgress(
    val items: List<BatchItemProgress>,
    val running: Boolean,
    val cancelled: Boolean,
) {
    val total: Int get() = items.size
    val finished: Int get() = items.count { it.state in setOf(BatchItemState.SUCCEEDED, BatchItemState.FAILED, BatchItemState.CANCELLED, BatchItemState.SKIPPED) }
    val overall: Float get() = if (items.isEmpty()) 0f else items.sumOf { it.progress.toDouble() }.toFloat() / items.size
    val succeeded: Int get() = items.count { it.state == BatchItemState.SUCCEEDED }
    val failed: Int get() = items.count { it.state == BatchItemState.FAILED }

    companion object { val IDLE = BatchProgress(emptyList(), running = false, cancelled = false) }
}
