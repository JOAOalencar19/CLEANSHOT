package com.cleanshot.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Motion
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing

/** Transparent top bar floating over the aurora background. */
@Composable
fun CleanShotTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    val c = CleanShotTheme.colors
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.sm, vertical = Spacing.xs).height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassSurface(Modifier.size(44.dp), shape = Radius.pill, onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = c.textPrimary, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(Spacing.sm))
        } else Spacer(Modifier.width(Spacing.xs))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Glass circular icon button. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null) {
    val c = CleanShotTheme.colors
    GlassSurface(modifier.size(44.dp), shape = Radius.pill, onClick = onClick) {
        Icon(icon, contentDescription, tint = tint ?: c.textPrimary, modifier = Modifier.align(Alignment.Center).size(22.dp))
    }
}

/** Expandable category container with animated chevron. */
@Composable
fun ExpandableSection(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = CleanShotTheme.colors
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.STANDARD, easing = Motion.Emphasized), label = "chevron")
    GlassSurface(modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(Radius.sm).background(iconTint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                trailing?.invoke()
                Spacer(Modifier.width(Spacing.xs))
                Icon(Icons.Rounded.ExpandMore, contentDescription = if (expanded) "Recolher" else "Expandir", tint = c.textSecondary, modifier = Modifier.rotate(rotation))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(Motion.STANDARD, easing = Motion.Emphasized)) + fadeIn(tween(Motion.STANDARD)),
                exit = shrinkVertically(tween(Motion.STANDARD, easing = Motion.Emphasized)) + fadeOut(tween(Motion.QUICK)),
            ) {
                Column(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)) { content() }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val c = CleanShotTheme.colors
    Column(modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(Radius.pill).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(Spacing.lg)); action() }
    }
}
