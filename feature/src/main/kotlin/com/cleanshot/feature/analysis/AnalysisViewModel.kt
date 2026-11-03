package com.cleanshot.feature.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanshot.domain.model.ImageRef
import com.cleanshot.domain.repository.ImageImportRepository
import com.cleanshot.domain.session.ImageSession
import com.cleanshot.domain.usecase.AnalyzeImageUseCase
import com.cleanshot.domain.usecase.AnalyzedImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AnalysisUiState {
    data object Loading : AnalysisUiState
    data class Ready(val sessionId: String, val image: AnalyzedImage) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val importer: ImageImportRepository,
    private val analyze: AnalyzeImageUseCase,
    private val session: ImageSession,
) : ViewModel() {

    private val uri: String = savedState.get<String>("uri").orEmpty()
    val next: String = savedState.get<String>("next").orEmpty()

    private val _state = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Loading)
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    private var sessionId: String? = null

    init { load() }

    fun load() {
        _state.value = AnalysisUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val imported = importer.describe(uri)
                val result = analyze(ImageRef(uri, imported.ref.displayName, imported.ref.mimeType, imported.ref.sizeBytes))
                val id = session.put(result)
                sessionId = id
                AnalysisUiState.Ready(id, result)
            } catch (e: SecurityException) {
                AnalysisUiState.Error("O acesso a este arquivo foi revogado. Selecione-o novamente.")
            } catch (e: Exception) {
                AnalysisUiState.Error(e.message ?: "Não foi possível analisar o arquivo.")
            }
        }
    }

    override fun onCleared() {
        sessionId?.let { session.discard(it) }
    }
}
