package com.cleanshot.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.HistoryStats
import com.cleanshot.domain.repository.HistoryRepository
import com.cleanshot.feature.common.PendingSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(val stats: HistoryStats = HistoryStats(0, 0, 0), val recent: List<HistoryRecord> = emptyList())

@HiltViewModel
class HomeViewModel @Inject constructor(
    history: HistoryRepository,
    private val pending: PendingSelection,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(history.observeStats(), history.observeRecent(6)) { s, r -> HomeUiState(s, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun stageMultiple(uris: List<String>) = pending.set(PendingSelection.Selection.Uris(uris))
    fun stageFolder(treeUri: String) = pending.set(PendingSelection.Selection.Folder(treeUri))
}
