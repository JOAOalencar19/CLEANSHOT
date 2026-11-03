package com.cleanshot.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cleanshot.core.common.Formatters
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.EmptyState
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.GlassIconButton
import com.cleanshot.core.designsystem.component.StatusPill
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.OperationOutcome
import com.cleanshot.domain.model.OperationType
import com.cleanshot.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repo: HistoryRepository) : ViewModel() {
    val records = repo.observeRecent(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun clear() = viewModelScope.launch { repo.clear() }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val c = CleanShotTheme.colors
    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Histórico", onBack) {
            if (!records.isNullOrEmpty()) GlassIconButton(Icons.Rounded.DeleteSweep, "Apagar histórico", { confirmClear = true })
        }
        val list = records
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Icons.Rounded.History, "Histórico vazio", "As operações aparecem aqui. Nenhuma foto, caminho ou metadado é armazenado — apenas nome, formato, tamanho e resultado.")
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Spacing.gutter).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                contentPadding = PaddingValues(bottom = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(list, key = { it.id }) { r -> HistoryRow(r) { viewModel.delete(r.id) } }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar histórico?") },
            text = { Text("Todos os registros serão removidos. Suas fotos não são afetadas.") },
            confirmButton = { TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text("Apagar", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HistoryRow(r: HistoryRecord, onDelete: () -> Unit) {
    val c = CleanShotTheme.colors
    val icon = when (r.operation) {
        OperationType.ANALYSIS -> Icons.Rounded.Insights
        OperationType.CLEANING -> Icons.Rounded.CleaningServices
        OperationType.EXPORT -> Icons.Rounded.IosShare
        OperationType.BATCH_CLEANING -> Icons.Rounded.Layers
    }
    GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Radius.sm).background(c.accentSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c.accent) }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(r.fileName, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${r.operation.label} · ${Formatters.dateTime(r.timestampMillis)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                val sizes = r.sizeAfter?.let { "${Formatters.size(r.sizeBefore)} → ${Formatters.size(it)}" } ?: Formatters.size(r.sizeBefore)
                val formats = listOfNotNull(r.originalFormat, r.resultFormat?.takeIf { it != r.originalFormat }).joinToString(" → ")
                Text("$formats · $sizes" + if (r.removedCount > 0) " · ${r.removedCount} removidos" else "", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                r.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.danger, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            StatusPill(r.outcome.label, if (r.outcome == OperationOutcome.SUCCESS) c.success else c.danger)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Apagar registro", tint = c.textTertiary) }
        }
    }
}
