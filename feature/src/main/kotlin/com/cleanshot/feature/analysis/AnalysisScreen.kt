package com.cleanshot.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanshot.core.common.Formatters
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.ExpandableSection
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.KeyValueRow
import com.cleanshot.core.designsystem.component.PrimaryButton
import com.cleanshot.core.designsystem.component.SecondaryButton
import com.cleanshot.core.designsystem.component.StatusPill
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.AnalysisReport
import com.cleanshot.domain.model.FindingStatus
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.domain.model.PrivacyLevel
import com.cleanshot.domain.usecase.AnalyzedImage
import com.cleanshot.feature.common.BottomActionBar
import com.cleanshot.feature.common.ErrorState
import com.cleanshot.feature.common.ImageThumb
import com.cleanshot.feature.common.LoadingState
import com.cleanshot.feature.common.NoticeCard
import com.cleanshot.feature.common.color
import com.cleanshot.feature.common.icon
import com.cleanshot.feature.common.isExpandedWidth
import com.cleanshot.feature.common.tint

@Composable
fun AnalysisScreen(
    onBack: () -> Unit,
    onClean: (String) -> Unit,
    onProvenance: (String) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var autoForwarded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Análise de metadados", onBack)
        when (val s = state) {
            AnalysisUiState.Loading -> LoadingState("Lendo EXIF, IPTC, XMP e credenciais C2PA localmente…")
            is AnalysisUiState.Error -> ErrorState("Não foi possível analisar", s.message, onBack)
            is AnalysisUiState.Ready -> {
                LaunchedEffect(s.sessionId) {
                    if (viewModel.next == "clean" && !autoForwarded) { autoForwarded = true; onClean(s.sessionId) }
                }
                AnalysisContent(s.image, onClean = { onClean(s.sessionId) }, onProvenance = { onProvenance(s.sessionId) })
            }
        }
    }
}

private enum class Filter(val label: String) { ALL("Todos"), FOUND("Encontrados"), NOT_FOUND("Não encontrados"), UNSUPPORTED("Não suportados"), UNVERIFIABLE("Não verificáveis") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisContent(image: AnalyzedImage, onClean: () -> Unit, onProvenance: () -> Unit) {
    val report = image.report
    var filter by remember { mutableStateOf(Filter.FOUND) }
    val expanded = remember { mutableStateMapOf<MetadataCategory, Boolean>().apply { put(MetadataCategory.LOCATION, true) } }
    var detail by remember { mutableStateOf<MetadataEntry?>(null) }
    val wide = isExpandedWidth()

    val sections = report.sections.map { sec ->
        sec to sec.entries.filter {
            when (filter) {
                Filter.ALL -> true
                Filter.FOUND -> it.status == FindingStatus.FOUND
                Filter.NOT_FOUND -> it.status == FindingStatus.NOT_FOUND
                Filter.UNSUPPORTED -> it.status == FindingStatus.UNSUPPORTED
                Filter.UNVERIFIABLE -> it.status == FindingStatus.UNVERIFIABLE
            }
        }
    }.filter { it.second.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            if (wide) {
                Column(Modifier.weight(0.42f).padding(top = Spacing.xs)) { Header(image, onProvenance) }
            }
            LazyColumn(
                Modifier.weight(if (wide) 0.58f else 1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Spacing.xs, bottom = 140.dp),
            ) {
                if (!wide) item { Header(image, onProvenance) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Filter.entries.forEach { f ->
                            val count = when (f) {
                                Filter.ALL -> report.allEntries.size
                                Filter.FOUND -> report.allEntries.count { it.status == FindingStatus.FOUND }
                                Filter.NOT_FOUND -> report.allEntries.count { it.status == FindingStatus.NOT_FOUND }
                                Filter.UNSUPPORTED -> report.allEntries.count { it.status == FindingStatus.UNSUPPORTED }
                                Filter.UNVERIFIABLE -> report.allEntries.count { it.status == FindingStatus.UNVERIFIABLE }
                            }
                            FilterChip(
                                selected = filter == f, onClick = { filter = f },
                                label = { Text("${f.label} ($count)") },
                                shape = Radius.pill,
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CleanShotTheme.colors.glassTop,
                                    labelColor = CleanShotTheme.colors.textSecondary,
                                    selectedContainerColor = CleanShotTheme.colors.accentSoft,
                                    selectedLabelColor = CleanShotTheme.colors.textPrimary,
                                ),
                                border = null,
                            )
                        }
                    }
                }
                items(sections, key = { it.first.category.name }) { (section, entries) ->
                    val open = expanded[section.category] == true
                    ExpandableSection(
                        title = section.category.title,
                        icon = section.category.icon(),
                        iconTint = section.category.tint(),
                        expanded = open,
                        onToggle = { expanded[section.category] = !open },
                        subtitle = "${entries.size} ${if (entries.size == 1) "item" else "itens"}",
                        trailing = {
                            val level = entries.filter { it.status == FindingStatus.FOUND }.maxOfOrNull { it.privacy } ?: PrivacyLevel.NONE
                            if (level >= PrivacyLevel.MEDIUM) StatusPill(level.label, level.color())
                        },
                    ) {
                        entries.forEach { EntryRow(it) { detail = it } }
                    }
                }
                if (sections.isEmpty()) item {
                    GlassCard(Modifier.fillMaxWidth()) { Text("Nenhum item neste filtro.", color = CleanShotTheme.colors.textSecondary) }
                }
                item { NoticeCard("Observações", report.warnings) }
            }
        }
        BottomActionBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.widthIn(max = 720.dp).align(Alignment.Center)) {
                SecondaryButton("Procedência", onProvenance, Modifier.weight(1f), icon = Icons.Rounded.AutoAwesome)
                PrimaryButton("Limpar", onClean, Modifier.weight(1.3f), icon = Icons.Rounded.CleaningServices, enabled = report.file.format.canClean)
            }
        }
    }

    detail?.let { entry ->
        ModalBottomSheet(onDismissRequest = { detail = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
            EntryDetail(entry)
        }
    }
}

@Composable
private fun Header(image: AnalyzedImage, onProvenance: () -> Unit) {
    val c = CleanShotTheme.colors
    val r = image.report
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(Radius.lg)) {
            ImageThumb(image.localCopy, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomStart).padding(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(r.file.format.displayName, c.accent)
                StatusPill(r.file.resolutionLabel, c.accentAlt)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text(r.file.displayName, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.xs))
            KeyValueRow("Formato", r.file.format.displayName)
            KeyValueRow("Dimensões", r.file.resolutionLabel + (r.file.megapixels?.let { " · %.1f MP".format(it) } ?: ""))
            KeyValueRow("Tamanho", Formatters.size(r.file.sizeBytes))
            r.file.colorProfile?.let { KeyValueRow("Perfil de cor", it) }
        }
        PrivacySummary(r)
        val v = r.provenance.verdict
        GlassCard(Modifier.fillMaxWidth(), onClick = onProvenance) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(Radius.sm).background(v.color().copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(v.icon(), null, tint = v.color())
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text("Procedência e IA", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text(v.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = c.textTertiary)
            }
        }
    }
}

@Composable
private fun PrivacySummary(r: AnalysisReport) {
    val c = CleanShotTheme.colors
    val found = r.foundEntries
    val high = found.count { it.privacy == PrivacyLevel.HIGH }
    val medium = found.count { it.privacy == PrivacyLevel.MEDIUM }
    val headline = when {
        r.hasLocation -> "Esta foto revela onde foi tirada"
        high > 0 -> "Há informações pessoais nesta foto"
        medium > 0 -> "Há dados que podem identificar você"
        else -> "Poucos dados sensíveis encontrados"
    }
    val tint = when { high > 0 -> c.privacyHigh; medium > 0 -> c.privacyMedium; else -> c.success }
    GlassCard(Modifier.fillMaxWidth(), tint = tint.copy(alpha = 0.06f)) {
        Text("Privacidade", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        Text(headline, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill("$high alto", c.privacyHigh)
            StatusPill("$medium médio", c.privacyMedium)
            StatusPill("${found.size} campos", c.accent)
        }
    }
}

@Composable
private fun EntryRow(e: MetadataEntry, onClick: () -> Unit) {
    val c = CleanShotTheme.colors
    Row(Modifier.fillMaxWidth().clip(Radius.sm).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(Radius.pill).background(if (e.status == FindingStatus.FOUND) e.privacy.color() else e.status.color()))
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Text(e.value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (e.status != FindingStatus.FOUND) StatusPill(e.status.label, e.status.color())
    }
}

@Composable
private fun EntryDetail(e: MetadataEntry) {
    val c = CleanShotTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
        Text(e.name, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(e.status.label, e.status.color())
            StatusPill("Privacidade: ${e.privacy.label}", e.privacy.color())
        }
        Spacer(Modifier.height(Spacing.md))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Valor", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            Text(e.value, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(e.description, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.height(Spacing.sm))
        KeyValueRow("Categoria", e.category.title)
        KeyValueRow("Padrão", e.standard.label)
        KeyValueRow("Remoção", e.removal.label, valueColor = e.removal.color())
        e.cleaningTarget?.let { KeyValueRow("Grupo de limpeza", it.title) }
        e.details?.let { KeyValueRow("Detalhes técnicos", it) }
    }
}
