package com.cleanshot.feature.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanshot.domain.model.BatchProgress
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.ImportedImage
import com.cleanshot.domain.repository.ImageImportRepository
import com.cleanshot.domain.repository.ProcessingQueue
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.feature.common.PendingSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

data class BatchSetupState(
    val loading: Boolean = true,
    val images: List<ImportedImage> = emptyList(),
    val failed: Int = 0,
    val options: CleaningOptions = CleaningOptions.DEFAULT,
    val destination: String? = null,
    val destinationLabel: String? = null,
)

@HiltViewModel
class BatchViewModel @Inject constructor(
    private val pending: PendingSelection,
    private val importer: ImageImportRepository,
    private val queue: ProcessingQueue,
    private val settings: SettingsRepository,
) : ViewModel() {

    val setup = MutableStateFlow(BatchSetupState())
    val progress: StateFlow<BatchProgress> = queue.progress.stateIn(viewModelScope, SharingStarted.Eagerly, BatchProgress.IDLE)

    init {
        viewModelScope.launch {
            setup.update { it.copy(options = settings.settings.first().defaultOptions) }
            val refs = when (val s = pending.consume()) {
                is PendingSelection.Selection.Uris -> s.uris
                is PendingSelection.Selection.Folder -> runCatching { importer.listTree(s.treeUri).map { it.uri } }.getOrDefault(emptyList())
                null -> emptyList()
            }
            val limiter = Semaphore(4)
            val described = refs.map { uri -> async { limiter.withPermit { runCatching { importer.describe(uri) }.getOrNull() } } }.awaitAll()
            setup.update { it.copy(loading = false, images = described.filterNotNull(), failed = described.count { d -> d == null }) }
        }
    }

    fun toggle(target: CleaningTarget) = setup.update { s ->
        s.copy(options = CleaningOptions(s.options.targets.toMutableSet().apply { if (!add(target)) remove(target) }))
    }

    fun remove(image: ImportedImage) = setup.update { s -> s.copy(images = s.images - image) }

    fun setDestination(uri: String, label: String?) = setup.update { it.copy(destination = uri, destinationLabel = label) }

    fun start() {
        val s = setup.value
        val dest = s.destination ?: return
        val eligible = s.images.filter { it.format.canClean }
        if (eligible.isEmpty() || s.options.isEmpty) return
        queue.enqueue(eligible.map { it.ref }, s.options, dest)
    }

    fun cancel() = queue.cancel()
    fun reset() = queue.clearFinished()
}
