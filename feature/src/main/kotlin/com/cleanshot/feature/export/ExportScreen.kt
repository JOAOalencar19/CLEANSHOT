package com.cleanshot.feature.export

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cleanshot.core.common.Formatters
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.KeyValueRow
import com.cleanshot.core.designsystem.component.PrimaryButton
import com.cleanshot.core.designsystem.component.ProgressBar
import com.cleanshot.core.designsystem.component.SecondaryButton
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.OperationOutcome
import com.cleanshot.domain.model.OperationType
import com.cleanshot.domain.repository.ExportManager
import com.cleanshot.domain.repository.HistoryRepository
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.session.ImageSession
import com.cleanshot.domain.util.OutputNaming
import com.cleanshot.feature.common.ErrorState
import com.cleanshot.feature.common.NoticeCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val fileName: String = "",
    val progress: Float? = null,
    val savedTo: String? = null,
    val shareUri: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedState: SavedStateHandle,
    session: ImageSession,
    private val exporter: ExportManager,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val entry = session.get(savedState.get<String>("sid").orEmpty())
    val state = MutableStateFlow(ExportUiState(fileName = entry?.cleaned?.output?.name ?: ""))

    fun rename(name: String) = state.update { it.copy(fileName = name, savedTo = null) }

    /** Final name: sanitized, and always with the real format's extension. */
    fun finalName(): String {
        val format = entry?.cleaned?.report?.format ?: return state.value.fileName
        val raw = OutputNaming.sanitize(state.value.fileName.substringBeforeLast('.'))
        return OutputNaming.cleanedName("$raw.${format.extensions.first()}", "", format)
    }

    fun save(destination: Uri) {
        val cleaned = entry?.cleaned ?: return
        state.update { it.copy(progress = 0f, error = null) }
        viewModelScope.launch {
            try {
                val result = exporter.saveTo(cleaned.output.absolutePath, destination.toString()) { p -> state.update { it.copy(progress = p) } }
                state.update { it.copy(progress = null, savedTo = result.displayName) }
                if (settings.settings.first().keepHistory) history.add(HistoryRecord(fileName = result.displayName, timestampMillis = System.currentTimeMillis(),
                    operation = OperationType.EXPORT, originalFormat = cleaned.report.format.displayName, resultFormat = cleaned.report.format.displayName,
                    sizeBefore = cleaned.report.originalSize, sizeAfter = result.sizeBytes, outcome = OperationOutcome.SUCCESS, removedCount = cleaned.report.removed.size))
            } catch (e: Exception) {
                state.update { it.copy(progress = null, error = e.message ?: "Falha ao salvar") }
            }
        }
    }

    fun share() {
        val cleaned = entry?.cleaned ?: return
        viewModelScope.launch {
            try {
                state.update { it.copy(shareUri = exporter.prepareShare(cleaned.output.absolutePath, finalName()), error = null) }
            } catch (e: Exception) {
                state.update { it.copy(error = e.message ?: "Falha ao compartilhar") }
            }
        }
    }

    fun consumeShare() = state.update { it.copy(shareUri = null) }
}

@Composable
fun ExportScreen(onBack: () -> Unit, onFinish: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val c = CleanShotTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cleaned = viewModel.entry?.cleaned
    val mime = cleaned?.report?.format?.mimeType ?: "image/*"
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri -> if (uri != null) viewModel.save(uri) }

    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Compartilhar cópia limpa"))
            viewModel.consumeShare()
        }
    }

    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Exportar", onBack)
        if (cleaned == null) { ErrorState("Nada para exportar", "A sessão expirou.", onBack); return@Column }
        val before = viewModel.entry!!.analyzed.report
        val r = cleaned.report
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).imePadding().navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Arquivo original", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    KeyValueRow("Nome", before.file.displayName)
                    KeyValueRow("Formato", before.file.format.displayName)
                    KeyValueRow("Resolução", before.file.resolutionLabel)
                    KeyValueRow("Tamanho", Formatters.size(r.originalSize))
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Arquivo resultante", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    KeyValueRow("Nome", viewModel.finalName())
                    KeyValueRow("Formato", r.format.displayName)
                    KeyValueRow("Resolução", before.file.resolutionLabel)
                    KeyValueRow("Tamanho", "${Formatters.size(r.resultSize)} · ${Formatters.sizeDelta(r.originalSize, r.resultSize)}")
                    KeyValueRow("Metadados alterados", "${r.removed.size} removidos")
                    KeyValueRow("Metadados preservados", r.preserved.size.toString())
                    KeyValueRow("Recodificação", if (r.reencoded) "Sim" else "Nenhuma — pixels idênticos", valueColor = if (r.reencoded) c.warning else c.success)
                }
                OutlinedTextField(
                    value = state.fileName, onValueChange = viewModel::rename, singleLine = true,
                    label = { Text("Nome do arquivo") },
                    supportingText = { Text("A extensão .${r.format.extensions.first()} é mantida para preservar o formato.") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.glassBorder),
                )
                state.progress?.let { ProgressBar(it) }
                state.savedTo?.let {
                    GlassCard(Modifier.fillMaxWidth(), tint = c.success.copy(alpha = 0.08f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = c.success)
                            Text("  Salvo como $it", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
                state.error?.let { NoticeCard("Erro", listOf(it), danger = true) }
                PrimaryButton("Salvar cópia", { create.launch(viewModel.finalName()) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.SaveAlt, loading = state.progress != null)
                SecondaryButton("Compartilhar", viewModel::share, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
                SecondaryButton("Concluir", onFinish, Modifier.fillMaxWidth())
                Text(
                    "A cópia só sai do aparelho se você a compartilhar. O CleanShot não possui acesso à internet.",
                    style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        }
    }
}
