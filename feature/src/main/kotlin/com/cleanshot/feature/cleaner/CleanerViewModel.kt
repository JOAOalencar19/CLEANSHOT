package com.cleanshot.feature.cleaner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningPlan
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.RemovalPolicy
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.session.ImageSession
import com.cleanshot.domain.usecase.CleanImageUseCase
import com.cleanshot.domain.usecase.PlanCleaningUseCase
import com.cleanshot.domain.util.OutputNaming
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanerUiState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val options: CleaningOptions = CleaningOptions.DEFAULT,
    val countsByTarget: Map<CleaningTarget, Int> = emptyMap(),
    val plan: CleaningPlan? = null,
    val confirming: Boolean = false,
    val running: Boolean = false,
    val error: String? = null,
    val doneSessionId: String? = null,
)

@HiltViewModel
class CleanerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val session: ImageSession,
    private val planCleaning: PlanCleaningUseCase,
    private val clean: CleanImageUseCase,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val sid: String = savedState.get<String>("sid").orEmpty()
    private val _state = MutableStateFlow(CleanerUiState())
    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = session.get(sid)
            if (entry == null) { _state.value = CleanerUiState(loaded = true, missing = true); return@launch }
            val counts = entry.analyzed.report.foundEntries
                .filter { it.removal == RemovalPolicy.REMOVABLE && it.cleaningTarget != null }
                .groupingBy { it.cleaningTarget!! }.eachCount()
            val defaults = settings.settings.first().defaultOptions
            _state.value = CleanerUiState(loaded = true, options = defaults, countsByTarget = counts)
            replan()
        }
    }

    fun toggle(target: CleaningTarget) {
        _state.update { s ->
            val t = s.options.targets.toMutableSet().apply { if (!add(target)) remove(target) }
            s.copy(options = CleaningOptions(t))
        }
        replan()
    }

    fun preset(options: CleaningOptions) { _state.update { it.copy(options = options) }; replan() }

    private fun replan() {
        val entry = session.get(sid) ?: return
        _state.update { it.copy(plan = planCleaning(entry.analyzed, it.options), error = null) }
    }

    fun requestConfirmation() = _state.update { it.copy(confirming = true) }
    fun dismissConfirmation() = _state.update { it.copy(confirming = false) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun run() {
        val entry = session.get(sid) ?: return
        _state.update { it.copy(confirming = false, running = true, error = null) }
        viewModelScope.launch {
            try {
                val suffix = settings.settings.first().fileNameSuffix
                val name = OutputNaming.cleanedName(entry.analyzed.ref.displayName, suffix, entry.analyzed.report.file.format)
                val cleaned = clean(entry.analyzed, _state.value.options, name)
                session.attachCleaned(sid, cleaned)
                _state.update { it.copy(running = false, doneSessionId = sid) }
            } catch (e: Exception) {
                _state.update { it.copy(running = false, error = e.message ?: "Falha no processamento") }
            }
        }
    }

    fun consumeDone() = _state.update { it.copy(doneSessionId = null) }
}
