package com.cleanshot.feature.cleaner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.PrimaryButton
import com.cleanshot.core.designsystem.component.SecondaryButton
import com.cleanshot.core.designsystem.component.SectionHeader
import com.cleanshot.core.designsystem.component.StatusPill
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningPlan
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.feature.common.BottomActionBar
import com.cleanshot.feature.common.ErrorState
import com.cleanshot.feature.common.LoadingState
import com.cleanshot.feature.common.NoticeCard

@Composable
fun CleanerScreen(onBack: () -> Unit, onDone: (String) -> Unit, viewModel: CleanerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = CleanShotTheme.colors

    LaunchedEffect(state.doneSessionId) {
        state.doneSessionId?.let { viewModel.consumeDone(); onDone(it) }
    }

    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Privacy Cleaner", onBack)
        when {
            !state.loaded -> LoadingState("Preparando…")
            state.missing -> ErrorState("Sessão expirada", "Selecione a imagem novamente.", onBack)
            state.running -> LoadingState("Reescrevendo metadados sem recodificar a imagem e verificando o resultado…")
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(bottom = 140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (state.countsByTarget.isEmpty()) item {
                        NothingPersonalCard(state.plan, Modifier.widthIn(max = 720.dp))
                    }
                    item {
                        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            SecondaryButton("Essencial", { viewModel.preset(CleaningOptions.DEFAULT) }, Modifier.weight(1f))
                            SecondaryButton("Máximo", { viewModel.preset(CleaningOptions.MAXIMUM) }, Modifier.weight(1f))
                        }
                    }
                    item {
                        GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            CleaningTarget.entries.forEachIndexed { i, t ->
                                TargetRow(t, state.countsByTarget[t] ?: 0, t in state.options.targets) { viewModel.toggle(t) }
                                if (i < CleaningTarget.entries.lastIndex) Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                    state.plan?.let { plan ->
                        item { PlanSummary(plan, Modifier.widthIn(max = 720.dp)) }
                        item { NoticeCard("Atenção", plan.warnings, Modifier.widthIn(max = 720.dp)) }
                    }
                    item {
                        GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Lock, null, tint = c.accentAlt)
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Sempre preservado", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Credenciais C2PA, declarações de origem/IA, orientação, perfil de cor, dimensões, transparência e os dados de pixels. " +
                                    "O arquivo original nunca é alterado: o resultado é uma nova cópia.",
                                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                            )
                        }
                    }
                }
                BottomActionBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                    val plan = state.plan
                    PrimaryButton(
                        text = when {
                            plan?.canProceed == true -> "Revisar e limpar (${plan.toRemove.size})"
                            state.countsByTarget.isEmpty() -> "Nenhum dado pessoal encontrado"
                            else -> "Ative um grupo encontrado"
                        },
                        onClick = viewModel::requestConfirmation,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.Center),
                        icon = Icons.Rounded.CleaningServices,
                        enabled = plan?.canProceed == true,
                    )
                }
            }
        }
    }

    if (state.confirming) state.plan?.let { ConfirmationSheet(it, onConfirm = viewModel::run, onDismiss = viewModel::dismissConfirmation) }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("Entendi") } },
            title = { Text("Operação interrompida") },
            text = { Text("$msg\n\nNenhum arquivo foi gerado e o original permanece intacto.") },
        )
    }
}

/** Explains why there is nothing to clean (e.g. AI-generated images carry no personal metadata). */
@Composable
private fun NothingPersonalCard(plan: CleaningPlan?, modifier: Modifier) {
    val c = CleanShotTheme.colors
    val provenance = plan?.protected.orEmpty().filter { it.removal == com.cleanshot.domain.model.RemovalPolicy.PROTECTED_PROVENANCE }
    GlassCard(modifier.fillMaxWidth(), tint = c.success.copy(alpha = 0.07f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = c.success)
            Spacer(Modifier.width(Spacing.xs))
            Text("Nenhum dado pessoal encontrado", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Esta imagem não contém localização, autoria, dados do aparelho ou outros metadados pessoais que possam ser removidos.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
        )
        if (provenance.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Ela contém informações de procedência (${provenance.size}), como credenciais C2PA ou declarações de geração por IA. " +
                    "Elas descrevem como a imagem foi criada, não identificam você, e são sempre preservadas.",
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun TargetRow(target: CleaningTarget, found: Int, checked: Boolean, onToggle: () -> Unit) {
    val c = CleanShotTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(target.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.width(Spacing.xs))
                if (found > 0) StatusPill("$found encontrado(s)", c.accent) else Text("não encontrado", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
            Text(target.description, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(
            checked = checked, onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedTrackColor = c.glassTop, uncheckedBorderColor = c.glassBorder),
        )
    }
}

@Composable
private fun PlanSummary(plan: CleaningPlan, modifier: Modifier) {
    val c = CleanShotTheme.colors
    GlassCard(modifier.fillMaxWidth()) {
        Text("Plano", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill("${plan.toRemove.size} remover", c.danger)
            StatusPill("${plan.toPreserve.size} manter", c.accent)
            StatusPill("${plan.protected.size} protegidos", c.accentAlt)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(plan.strategy, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        if (!plan.canProceed && plan.blockingReason != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(plan.blockingReason!!, style = MaterialTheme.typography.bodySmall, color = c.warning)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationSheet(plan: CleaningPlan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = CleanShotTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Text("Confirmar limpeza", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.padding(end = 6.dp))
                Text(
                    if (plan.requiresReencoding) "A imagem precisará ser recodificada." else "Sem recodificação: os pixels serão copiados byte a byte.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textPrimary,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            PlanGroup("Será removido", plan.toRemove, c.danger)
            PlanGroup("Protegido (nunca removido)", plan.protected, c.accentAlt)
            PlanGroup("Não suportado (mantido sem alteração)", plan.unsupported, c.warning)
            Text("Mantido: ${plan.toPreserve.size} campo(s) não selecionados.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            if (plan.warnings.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                NoticeCard("Antes de continuar", plan.warnings)
            }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton("Confirmar e gerar cópia limpa", onConfirm, Modifier.fillMaxWidth(), icon = Icons.Rounded.CleaningServices)
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton("Cancelar", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlanGroup(title: String, entries: List<MetadataEntry>, color: androidx.compose.ui.graphics.Color) {
    if (entries.isEmpty()) return
    val c = CleanShotTheme.colors
    SectionHeader("$title (${entries.size})")
    entries.groupBy { it.category }.forEach { (cat, list) ->
        Text(cat.title, style = MaterialTheme.typography.labelMedium, color = color)
        Text(list.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(Spacing.xs))
}
