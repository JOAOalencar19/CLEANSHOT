package com.cleanshot.feature.provenance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.cleanshot.core.designsystem.component.CleanShotTopBar
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.KeyValueRow
import com.cleanshot.core.designsystem.component.StatusPill
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import com.cleanshot.domain.model.C2paManifestInfo
import com.cleanshot.domain.model.ProvenanceReport
import com.cleanshot.domain.model.ProvenanceVerdict
import com.cleanshot.domain.session.ImageSession
import com.cleanshot.feature.common.ErrorState
import com.cleanshot.feature.common.color
import com.cleanshot.feature.common.icon
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProvenanceViewModel @Inject constructor(savedState: SavedStateHandle, session: ImageSession) : ViewModel() {
    val report: ProvenanceReport? = session.get(savedState.get<String>("sid").orEmpty())?.analyzed?.report?.provenance
}

@Composable
fun ProvenanceScreen(onBack: () -> Unit, viewModel: ProvenanceViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize()) {
        CleanShotTopBar("AI Provenance Inspector", onBack)
        val r = viewModel.report
        if (r == null) { ErrorState("Análise indisponível", "A sessão expirou.", onBack); return@Column }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                VerdictHero(r.verdict)
                CategoryOverview(r)
                if (r.aiDeclarations.isNotEmpty()) {
                    Card("Declarações sobre IA e origem", Icons.Rounded.AutoAwesome, com.cleanshot.core.designsystem.theme.Palette.Iris) {
                        r.aiDeclarations.forEach { d ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.statement, style = MaterialTheme.typography.bodyMedium, color = CleanShotTheme.colors.textPrimary)
                                    Text(d.source, style = MaterialTheme.typography.labelSmall, color = CleanShotTheme.colors.textTertiary)
                                }
                                Spacer(Modifier.width(Spacing.xs))
                                StatusPill(if (d.signed) "Assinada" else "Não assinada", if (d.signed) CleanShotTheme.colors.success else CleanShotTheme.colors.warning)
                            }
                        }
                    }
                }
                r.manifests.sortedByDescending { it.isActive }.forEach { ManifestCard(it) }
                if (r.declaredTools.isNotEmpty()) {
                    Card("Ferramentas declaradas", Icons.Rounded.Build, CleanShotTheme.colors.info) {
                        r.declaredTools.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = CleanShotTheme.colors.textSecondary) }
                    }
                }
                if (r.notes.isNotEmpty()) {
                    Card("Observações", Icons.Rounded.Info, CleanShotTheme.colors.accent) {
                        r.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = CleanShotTheme.colors.textSecondary) }
                    }
                }
                Card("O que esta análise não faz", Icons.Rounded.Info, CleanShotTheme.colors.warning) {
                    r.limitations.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = CleanShotTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 2.dp)) }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun VerdictHero(v: ProvenanceVerdict) {
    val c = CleanShotTheme.colors
    GlassCard(Modifier.fillMaxWidth(), tint = v.color().copy(alpha = 0.08f)) {
        Box(Modifier.size(56.dp).clip(Radius.md).background(v.color().copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(v.icon(), null, tint = v.color(), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(v.title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Text(v.explanation, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

/** Shows every result category of the inspector and which ones apply to this file. */
@Composable
private fun CategoryOverview(r: ProvenanceReport) {
    val c = CleanShotTheme.colors
    val active = r.activeManifest
    val rows = listOf(
        "Credenciais encontradas" to r.c2paPresent,
        "Credenciais verificadas" to (r.verdict == ProvenanceVerdict.CREDENTIALS_VERIFIED),
        "Credenciais inválidas" to (r.verdict == ProvenanceVerdict.CREDENTIALS_INVALID),
        "Origem declarada" to r.aiDeclarations.isNotEmpty(),
        "Informações inconclusivas" to (r.verdict == ProvenanceVerdict.INCONCLUSIVE || (active?.checks?.any { it.state == com.cleanshot.domain.model.CheckState.NOT_CHECKED } == true)),
        "Nenhuma credencial encontrada" to !r.c2paPresent,
    )
    GlassCard(Modifier.fillMaxWidth()) {
        rows.forEach { (label, on) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(Radius.pill).background(if (on) c.accent else c.glassBorder))
                Spacer(Modifier.width(Spacing.sm))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (on) c.textPrimary else c.textTertiary, modifier = Modifier.weight(1f))
                Text(if (on) "Sim" else "Não", style = MaterialTheme.typography.labelMedium, color = if (on) c.accent else c.textTertiary)
            }
        }
    }
}

@Composable
private fun ManifestCard(m: C2paManifestInfo) {
    val c = CleanShotTheme.colors
    Card(if (m.isActive) "Manifesto ativo" else "Manifesto anterior", Icons.Rounded.VerifiedUser, c.accentAlt) {
        m.claimGenerator?.let { KeyValueRow("Gerador", it) }
        m.title?.let { KeyValueRow("Título", it) }
        m.format?.let { KeyValueRow("Formato", it) }
        m.signer?.let { s ->
            KeyValueRow("Assinante", s.subject)
            KeyValueRow("Emissor", s.issuer)
            KeyValueRow("Validade", "${s.validFrom} – ${s.validUntil}")
            s.algorithm?.let { KeyValueRow("Algoritmo", it) }
        }
        if (m.actions.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Ações declaradas", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            m.actions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.textPrimary) }
        }
        if (m.ingredients.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Ingredientes", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            m.ingredients.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.textPrimary) }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text("Verificações", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        m.checks.forEach { ch ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(ch.name, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    Text(ch.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Spacer(Modifier.width(Spacing.xs))
                StatusPill(ch.state.label, ch.state.color())
            }
        }
        if (m.assertions.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text("Asserções: " + m.assertions.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }
}

@Composable
private fun Card(title: String, icon: ImageVector, tint: Color, content: @Composable () -> Unit) {
    val c = CleanShotTheme.colors
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(Spacing.xs))
        content()
    }
}

