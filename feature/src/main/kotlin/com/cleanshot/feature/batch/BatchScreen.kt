package com.cleanshot.feature.batch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanshot.core.common.Formatters
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.EmptyState
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.PrimaryButton
import com.cleanshot.core.designsystem.component.ProgressBar
import com.cleanshot.core.designsystem.component.ProgressRing
import com.cleanshot.core.designsystem.component.SecondaryButton
import com.cleanshot.core.designsystem.component.StatusPill
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.BatchItemProgress
import com.cleanshot.domain.model.BatchItemState
import com.cleanshot.domain.model.BatchProgress
import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.ImportedImage
import com.cleanshot.feature.common.BottomActionBar
import com.cleanshot.feature.common.ImageThumb
import com.cleanshot.feature.common.LoadingState
import com.cleanshot.feature.common.persistUriPermission

@Composable
fun BatchScreen(onBack: () -> Unit, viewModel: BatchViewModel = hiltViewModel()) {
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            persistUriPermission(context, uri, write = true)
            viewModel.setDestination(uri.toString(), DocumentFile.fromTreeUri(context, uri)?.name)
        }
    }
    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Processamento em lote", onBack)
        when {
            progress.items.isNotEmpty() -> RunView(progress, onCancel = viewModel::cancel, onDone = { viewModel.reset(); onBack() })
            setup.loading -> LoadingState("Lendo informações das imagens selecionadas…")
            setup.images.isEmpty() -> EmptyState(Icons.Rounded.PhotoLibrary, "Nenhuma imagem", "Selecione várias fotos ou uma pasta no início.", action = { SecondaryButton("Voltar", onBack) })
            else -> SetupView(setup, viewModel, onPickFolder = { pickFolder.launch(null) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupView(s: BatchSetupState, vm: BatchViewModel, onPickFolder: () -> Unit) {
    val c = CleanShotTheme.colors
    val totalSize = s.images.sumOf { it.ref.sizeBytes.coerceAtLeast(0) }
    val unsupported = s.images.count { !it.format.canClean }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(bottom = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Text("${s.images.size} imagens · ${Formatters.size(totalSize)}", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    val gps = s.images.count { it.hasLocationHint == true }
                    if (gps > 0) Text("$gps com localização detectada", style = MaterialTheme.typography.bodyMedium, color = c.privacyHigh)
                    if (unsupported > 0) Text("$unsupported em formato somente para análise (serão ignoradas)", style = MaterialTheme.typography.bodySmall, color = c.warning)
                    if (s.failed > 0) Text("${s.failed} não puderam ser lidas", style = MaterialTheme.typography.bodySmall, color = c.danger)
                }
            }
            item {
                GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Text("Remover", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CleaningTarget.entries.forEach { t ->
                            FilterChip(
                                selected = t in s.options.targets, onClick = { vm.toggle(t) }, label = { Text(t.title) }, shape = Radius.pill,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.accentSoft, containerColor = c.glassTop, labelColor = c.textSecondary, selectedLabelColor = c.textPrimary),
                                border = null,
                            )
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth(), onClick = onPickFolder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CreateNewFolder, null, tint = c.accent)
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text("Pasta de destino", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(s.destinationLabel ?: if (s.destination != null) "Pasta selecionada" else "Toque para escolher onde salvar as cópias", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            }
            itemsIndexed(s.images, key = { _, it -> it.ref.uri }) { _, img -> ImageRow(img) { vm.remove(img) } }
        }
        BottomActionBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            PrimaryButton(
                if (s.destination == null) "Escolha a pasta de destino" else "Iniciar limpeza (${s.images.size - unsupported})",
                vm::start, Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.Center), icon = Icons.Rounded.PlayArrow,
                enabled = s.destination != null && !s.options.isEmpty && s.images.size > unsupported,
            )
        }
    }
}

@Composable
private fun ImageRow(img: ImportedImage, onRemove: () -> Unit) {
    val c = CleanShotTheme.colors
    GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImageThumb(img.ref.uri, Modifier.size(56.dp).clip(Radius.sm))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(img.ref.displayName, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(img.format.displayName, if (img.width != null && img.height != null) "${img.width}×${img.height}" else null, Formatters.size(img.ref.sizeBytes)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                if (img.hasLocationHint == true) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = c.privacyHigh, modifier = Modifier.size(14.dp))
                    Text(" contém GPS", style = MaterialTheme.typography.labelSmall, color = c.privacyHigh)
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remover da lista", tint = c.textTertiary) }
        }
    }
}

@Composable
private fun RunView(p: BatchProgress, onCancel: () -> Unit, onDone: () -> Unit) {
    val c = CleanShotTheme.colors
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(p.overall, size = 96.dp, stroke = 8.dp) {
                            Text("${(p.overall * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                when { p.running -> "Processando…"; p.cancelled -> "Cancelado"; else -> "Concluído" },
                                style = MaterialTheme.typography.headlineSmall, color = c.textPrimary,
                            )
                            Text("${p.finished} de ${p.total}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill("${p.succeeded} ok", c.success)
                                if (p.failed > 0) StatusPill("${p.failed} falhas", c.danger)
                            }
                        }
                    }
                    if (!p.running) {
                        Spacer(Modifier.height(Spacing.sm))
                        val before = p.items.filter { it.state == BatchItemState.SUCCEEDED }.sumOf { it.sizeBefore }
                        val after = p.items.filter { it.state == BatchItemState.SUCCEEDED }.sumOf { it.sizeAfter ?: 0 }
                        Text("Total: ${Formatters.size(before)} → ${Formatters.size(after)} · ${p.items.sumOf { it.removedCount }} metadados removidos",
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
            itemsIndexed(p.items, key = { i, _ -> i }) { _, item -> ProgressRow(item) }
        }
        BottomActionBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.Center)) {
                if (p.running) SecondaryButton("Cancelar", onCancel, Modifier.fillMaxWidth(), icon = Icons.Rounded.Cancel)
                else PrimaryButton("Concluir", onDone, Modifier.fillMaxWidth(), icon = Icons.Rounded.CheckCircle)
            }
        }
    }
}

@Composable
private fun ProgressRow(item: BatchItemProgress) {
    val c = CleanShotTheme.colors
    val (label, color, icon) = when (item.state) {
        BatchItemState.QUEUED -> Triple("Na fila", c.textTertiary, null)
        BatchItemState.RUNNING -> Triple("Processando", c.accent, null)
        BatchItemState.SUCCEEDED -> Triple("Concluído", c.success, Icons.Rounded.CheckCircle)
        BatchItemState.FAILED -> Triple("Falhou", c.danger, Icons.Rounded.ErrorOutline)
        BatchItemState.CANCELLED -> Triple("Cancelado", c.warning, Icons.Rounded.Cancel)
        BatchItemState.SKIPPED -> Triple("Nada a remover", c.textSecondary, Icons.Rounded.SkipNext)
    }
    GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ImageThumb(item.ref.uri, Modifier.size(44.dp).clip(Radius.sm))
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(item.ref.displayName, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                ProgressBar(item.progress, color = if (item.state == BatchItemState.FAILED) c.danger else null)
                item.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (item.state == BatchItemState.FAILED) c.danger else c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.width(Spacing.xs))
            StatusPill(label, color, icon = icon)
        }
    }
}
