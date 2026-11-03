package com.cleanshot.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cleanshot.core.common.Formatters
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.StatusPill
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.feature.common.ErrorState
import com.cleanshot.feature.common.ImageThumb

/** Side-by-side comparison of the original private copy and the cleaned copy. */
@Composable
fun CompareScreen(onBack: () -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    val c = CleanShotTheme.colors
    val entry = viewModel.entry
    val cleaned = entry?.cleaned
    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("Antes e depois", onBack)
        if (entry == null || cleaned == null) { ErrorState("Comparação indisponível", "A sessão expirou.", onBack); return@Column }
        val before = entry.analyzed.report
        val r = cleaned.report
        val removedKeys = r.removed.map { it.key }.toSet()
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 900.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Column(Modifier.weight(1f)) {
                        StatusPill("Original", c.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        ImageThumb(entry.analyzed.localCopy, Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.md))
                    }
                    Column(Modifier.weight(1f)) {
                        StatusPill("Limpa", c.success)
                        Spacer(Modifier.height(6.dp))
                        ImageThumb(cleaned.output, Modifier.fillMaxWidth().aspectRatio(0.8f).clip(Radius.md))
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Os pixels são idênticos: a diferença está apenas nos metadados. A verificação estrutural comparou os dados de imagem por SHA-256.",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                Spacer(Modifier.height(Spacing.md))
                GlassCard(Modifier.fillMaxWidth()) {
                    CompareRow("", "Original", "Limpa", header = true)
                    CompareRow("Formato", before.file.format.displayName, r.format.displayName)
                    CompareRow("Resolução", before.file.resolutionLabel, before.file.resolutionLabel)
                    CompareRow("Tamanho", Formatters.size(r.originalSize), Formatters.size(r.resultSize))
                    CompareRow("Campos de metadados", r.found.size.toString(), r.preserved.size.toString())
                    MetadataCategory.entries.filter { it != MetadataCategory.FILE }.forEach { cat ->
                        val b = r.found.count { it.category == cat }
                        if (b > 0) CompareRow(cat.title, b.toString(), r.preserved.count { it.category == cat }.toString())
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Diferenças campo a campo", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(Spacing.xs))
                    r.found.forEach { e ->
                        val removed = e.key in removedKeys
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                                Text(e.value, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            StatusPill(if (removed) "Removido" else "Mantido", if (removed) c.danger else c.accent)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String, header: Boolean = false) {
    val c = CleanShotTheme.colors
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = style, color = c.textSecondary, modifier = Modifier.weight(1.2f))
        Text(a, style = style, color = if (header) c.textSecondary else c.textPrimary, modifier = Modifier.weight(1f))
        Text(b, style = style, color = if (header) c.textSecondary else c.textPrimary, modifier = Modifier.weight(1f))
    }
}
