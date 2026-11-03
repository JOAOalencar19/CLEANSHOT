package com.cleanshot.data.batch

import com.cleanshot.domain.model.BatchItemProgress
import com.cleanshot.domain.model.BatchItemState
import com.cleanshot.domain.model.BatchProgress
import com.cleanshot.domain.model.ImageRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Observable state of the running batch, updated by the worker. */
@Singleton
class BatchStateStore @Inject constructor() {
    private val _state = MutableStateFlow(BatchProgress.IDLE)
    val state: StateFlow<BatchProgress> = _state.asStateFlow()

    fun start(refs: List<ImageRef>) {
        _state.value = BatchProgress(refs.map { BatchItemProgress(it, BatchItemState.QUEUED, 0f) }, running = true, cancelled = false)
    }

    fun ensureItems(refs: List<ImageRef>) {
        if (_state.value.items.map { it.ref.uri } != refs.map { it.uri }) start(refs)
        else _state.update { it.copy(running = true) }
    }

    fun item(index: Int, transform: (BatchItemProgress) -> BatchItemProgress) = _state.update { s ->
        if (index !in s.items.indices) s else s.copy(items = s.items.toMutableList().also { it[index] = transform(it[index]) })
    }

    fun finish(cancelled: Boolean) = _state.update { s ->
        s.copy(
            running = false,
            cancelled = cancelled,
            items = s.items.map { if (it.state == BatchItemState.QUEUED || it.state == BatchItemState.RUNNING) it.copy(state = BatchItemState.CANCELLED, progress = 1f) else it },
        )
    }

    fun clear() { _state.value = BatchProgress.IDLE }
}
