package com.cleanshot.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.cleanshot.domain.model.MetadataEntry
import com.cleanshot.feature.common.BottomActionBar
import com.cleanshot.feature.common.ErrorState
import com.cleanshot.feature.common.ImageThumb
import com.cleanshot.feature.common.NoticeCard

@Composable
fun ReportScreen(onBack: () -> Unit, onExport: (String) -> Unit, onCompare: (String) -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    val c = CleanShotTheme.colors
    val cleaned = viewModel.entry?.cleaned
    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Relatório de limpeza", onBack)
        if (cleaned == null) { ErrorState("Relatório indisponível", "A sessão expirou. Processe a imagem novamente.", onBack); return@Column }
        val r = cleaned.report
        val expanded = remember { mutableStateMapOf<String, Boolean>() }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(bottom = 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth(), tint = c.success.copy(alpha = 0.07f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(Radius.pill).background(c.success.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.DoneAll, null, tint = c.success, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Column {
                                Text("Cópia limpa gerada", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                                Text("${r.removed.size} removidos · ${r.preserved.size} preservados", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(Spacing.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ImageThumb(cleaned.output, Modifier.weight(1f).aspectRatio(1f).clip(Radius.md))
                            Column(Modifier.weight(1.4f)) {
                                KeyValueRow("Formato", "${r.format.displayName} (mantido)")
                                KeyValueRow("Antes", Formatters.size(r.originalSize))
                                KeyValueRow("Depois", Formatters.size(r.resultSize))
                                KeyValueRow("Diferença", Formatters.sizeDelta(r.originalSize, r.resultSize))
                                KeyValueRow("Recodificação", if (r.reencoded) "Sim" else "Nenhuma", valueColor = if (r.reencoded) c.warning else c.success)
                            }
                        }
                    }
                }
                item {
                    GlassCard(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        Text("Verificações de preservação", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Spacer(Modifier.height(Spacing.xs))
                        r.preservationChecks.forEach { ch ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Icon(if (ch.passed) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, tint = if (ch.passed) c.success else c.danger, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Column {
                                    Text(ch.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                                    Text(ch.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                item { NoticeCard("Avisos", r.warnings, Modifier.widthIn(max = 720.dp)) }
                val groups = listOf(
                    Triple("Removidos", r.removed, Icons.Rounded.RemoveCircleOutline to c.danger),
                    Triple("Preservados", r.preserved, Icons.Rounded.Lock to c.accent),
                    Triple("Encontrados", r.found, Icons.Rounded.Layers to c.info),
                    Triple("Não suportados", r.unsupported, Icons.Rounded.WarningAmber to c.warning),
                )
                groups.forEach { (title, list, style) ->
                    if (list.isNotEmpty()) item(key = title) {
                        Group(title, list, style.first, style.second, expanded[title] == true, Modifier.widthIn(max = 720.dp)) { expanded[title] = expanded[title] != true }
                    }
                }
                if (r.changes.isNotEmpty()) item {
                    ExpandableSection("Alterações no arquivo", Icons.Rounded.Shield, c.accentAlt, expanded["changes"] == true, { expanded["changes"] = expanded["changes"] != true },
                        Modifier.widthIn(max = 720.dp), subtitle = "${r.changes.size} operação(ões) estruturais") {
                        r.changes.forEach { Text("• ${it.description}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                }
            }
            BottomActionBar(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                Row(Modifier.widthIn(max = 720.dp).align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton("Comparar", { onCompare(viewModel.sessionId) }, Modifier.weight(1f), icon = Icons.Rounded.Compare)
                    PrimaryButton("Exportar", { onExport(viewModel.sessionId) }, Modifier.weight(1.3f), icon = Icons.Rounded.IosShare)
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, list: List<MetadataEntry>, icon: ImageVector, tint: Color, expanded: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val c = CleanShotTheme.colors
    ExpandableSection(title, icon, tint, expanded, onToggle, modifier, subtitle = "${list.size} campo(s)") {
        list.groupBy { it.category }.forEach { (cat, entries) ->
            Text(cat.title, style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.padding(top = 6.dp))
            entries.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(e.name, style = MaterialTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.weight(0.45f))
                    Text(e.value, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.55f))
                }
            }
        }
    }
}
