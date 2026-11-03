package com.cleanshot.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.SecondaryButton
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.repository.ThemeMode
import com.cleanshot.domain.repository.UserSettings
import com.cleanshot.domain.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository, private val workspace: WorkspaceRepository) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    fun theme(m: ThemeMode) = viewModelScope.launch { repo.setThemeMode(m) }
    fun toggle(t: CleaningTarget) = viewModelScope.launch {
        val cur = settings.value.defaultOptions.targets.toMutableSet().apply { if (!add(t)) remove(t) }
        repo.setDefaultOptions(CleaningOptions(cur))
    }
    fun suffix(s: String) = viewModelScope.launch { repo.setFileNameSuffix(s) }
    fun keepHistory(k: Boolean) = viewModelScope.launch { repo.setKeepHistory(k) }
    fun clearTemp(done: () -> Unit) = viewModelScope.launch { workspace.clearAll(); done() }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val c = CleanShotTheme.colors
    var cleared by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Configurações", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Aparência", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(Spacing.xs))
                    val modes = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = s.themeMode == m, onClick = { viewModel.theme(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                colors = SegmentedButtonDefaults.colors(activeContainerColor = c.accentSoft, activeContentColor = c.textPrimary, inactiveContainerColor = c.glassBottom, inactiveContentColor = c.textSecondary),
                            ) { Text(label) }
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Limpeza padrão", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("Grupos pré-selecionados ao limpar uma imagem ou um lote.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    CleaningTarget.entries.forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(t in s.defaultOptions.targets, { viewModel.toggle(t) }, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Nome das cópias", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = s.fileNameSuffix, onValueChange = viewModel::suffix, singleLine = true,
                        label = { Text("Sufixo") }, supportingText = { Text("Exemplo: IMG_1234${s.fileNameSuffix}.jpg") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.glassBorder),
                    )
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Registrar histórico", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            Text("Guarda apenas nome, data, formato, tamanho e resultado.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Switch(s.keepHistory, viewModel::keepHistory, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Privacidade", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(Spacing.xs))
                    listOf(
                        "Todo o processamento acontece no aparelho; o app não declara permissão de internet.",
                        "Sem anúncios, sem rastreamento, sem análise de uso.",
                        "O arquivo original nunca é modificado: o CleanShot trabalha em cópias privadas, apagadas após o uso.",
                        "Não são pedidas permissões amplas de armazenamento: o acesso vem do seletor de fotos/arquivos.",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = 2.dp)) }
                    Spacer(Modifier.height(Spacing.sm))
                    SecondaryButton(if (cleared) "Arquivos temporários apagados" else "Apagar arquivos temporários", { viewModel.clearTemp { cleared = true } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteSweep)
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Sobre o CleanShot", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        "Analisa EXIF, IPTC, XMP e credenciais C2PA. Remove metadados convencionais sem recodificar JPEG, PNG, WebP, HEIF e AVIF. " +
                            "Credenciais de procedência e declarações de IA são sempre preservadas. A verificação C2PA confere assinatura e integridade, " +
                            "mas não a lista de confiança dos emissores.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}
