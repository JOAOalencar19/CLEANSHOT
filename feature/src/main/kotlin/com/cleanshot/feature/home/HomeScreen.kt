package com.cleanshot.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanshot.core.common.Formatters
import com.cleanshot.core.designsystem.component.CleanShotWordmark
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.GlassIconButton
import com.cleanshot.core.designsystem.component.MetricTile
import com.cleanshot.core.designsystem.component.PrimaryButton
import com.cleanshot.core.designsystem.component.SecondaryButton
import com.cleanshot.core.designsystem.component.SectionHeader
import com.cleanshot.core.designsystem.component.StatusPill
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Motion
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.HistoryRecord
import com.cleanshot.domain.model.OperationOutcome
import com.cleanshot.feature.common.isMediumWidth
import com.cleanshot.feature.common.persistUriPermission
import androidx.compose.foundation.clickable


@Composable
fun HomeScreen(
    onAnalyze: (String) -> Unit,
    onAnalyzeThenClean: (String) -> Unit,
    onBatch: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val c = CleanShotTheme.colors
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cleanAfterPick by remember { mutableStateOf(false) }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            persistUriPermission(context, uri)
            if (cleanAfterPick) onAnalyzeThenClean(uri.toString()) else onAnalyze(uri.toString())
        }
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> { persistUriPermission(context, uris[0]); onAnalyze(uris[0].toString()) }
            else -> { uris.forEach { persistUriPermission(context, it) }; viewModel.stageMultiple(uris.map { it.toString() }); onBatch() }
        }
    }
    val openDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> { persistUriPermission(context, uris[0]); onAnalyze(uris[0].toString()) }
            else -> { uris.forEach { persistUriPermission(context, it) }; viewModel.stageMultiple(uris.map { it.toString() }); onBatch() }
        }
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { persistUriPermission(context, uri); viewModel.stageFolder(uri.toString()); onBatch() }
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 920.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    CleanShotWordmark(Modifier.weight(1f))
                    GlassIconButton(Icons.Rounded.Settings, "Configurações", onSettings)
                }
                Spacer(Modifier.height(Spacing.xl))

                AnimatedVisibility(appeared, enter = fadeIn(tween(Motion.EMPHASIZED)) + slideInVertically(tween(Motion.EMPHASIZED, easing = Motion.Emphasized)) { it / 6 }) {
                    Column {
                        StatusPill("100% local · sem internet", c.accentAlt, icon = Icons.Rounded.Lock)
                        Spacer(Modifier.height(Spacing.md))
                        Text("Suas fotos contam mais do que você imagina.", style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            "Descubra localização, autoria e dados do aparelho escondidos nas imagens, remova o que for pessoal " +
                                "sem perder qualidade e confira credenciais de procedência — tudo no seu dispositivo.",
                            style = MaterialTheme.typography.bodyLarge, color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))

                val wide = isMediumWidth()
                val primary: @Composable (Modifier) -> Unit = { m ->
                    PrimaryButton("Selecionar fotografia", { cleanAfterPick = false; pickOne.launch(imagesOnly) }, m, icon = Icons.Rounded.AddPhotoAlternate)
                }
                val secondary: @Composable (Modifier) -> Unit = { m ->
                    SecondaryButton("Selecionar várias", { pickMany.launch(imagesOnly) }, m, icon = Icons.Rounded.Collections)
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { primary(Modifier.weight(1f)); secondary(Modifier.weight(1f)) }
                } else {
                    primary(Modifier.fillMaxWidth()); Spacer(Modifier.height(Spacing.sm)); secondary(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SourceChip("Arquivos", Icons.Rounded.InsertDriveFile, Modifier.weight(1f)) { openDocs.launch(arrayOf("image/*")) }
                    SourceChip("Pasta", Icons.Rounded.FolderOpen, Modifier.weight(1f)) { openTree.launch(null) }
                }

                Spacer(Modifier.height(Spacing.xl))
                SectionHeader("Atalhos")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Shortcut("Analisar", "Inspecionar metadados", Icons.Rounded.Insights, c.accent, Modifier.weight(1f)) { cleanAfterPick = false; pickOne.launch(imagesOnly) }
                    Shortcut("Limpar", "Remover dados pessoais", Icons.Rounded.CleaningServices, c.accentAlt, Modifier.weight(1f)) { cleanAfterPick = true; pickOne.launch(imagesOnly) }
                    Shortcut("Histórico", "Operações anteriores", Icons.Rounded.History, com.cleanshot.core.designsystem.theme.Palette.Iris, Modifier.weight(1f), onHistory)
                }

                Spacer(Modifier.height(Spacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MetricTile(state.stats.analyzedFiles.toString(), "arquivos analisados", Icons.Rounded.Insights, c.accent, Modifier.weight(1f))
                    MetricTile(state.stats.cleanedFiles.toString(), "cópias limpas", Icons.Rounded.TaskAlt, c.success, Modifier.weight(1f))
                }

                Spacer(Modifier.height(Spacing.xl))
                SectionHeader("Últimas imagens processadas", subtitle = "Apenas nomes e resultados — nenhuma foto é guardada", trailing = {
                    if (state.recent.isNotEmpty()) Text("Ver tudo", style = MaterialTheme.typography.labelLarge, color = c.accent, modifier = Modifier.clip(Radius.pill).clickable(onClick = onHistory).padding(8.dp))
                })
                if (state.recent.isEmpty()) {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Nenhuma imagem processada ainda.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                } else {
                    GlassCard(Modifier.fillMaxWidth(), onClick = onHistory, contentPadding = PaddingValues(vertical = Spacing.xs, horizontal = Spacing.md)) {
                        state.recent.forEach { RecentRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    SecondaryButton(label, onClick, modifier, icon = icon)
}

@Composable
private fun Shortcut(title: String, subtitle: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val c = CleanShotTheme.colors
    GlassCard(modifier, onClick = onClick) {
        Box(Modifier.size(40.dp).clip(Radius.sm).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentRow(r: HistoryRecord) {
    val c = CleanShotTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.fileName, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.operation.label} · ${Formatters.dateTime(r.timestampMillis)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(Spacing.xs))
        StatusPill(r.outcome.label, if (r.outcome == OperationOutcome.SUCCESS) c.success else c.danger)
    }
}
