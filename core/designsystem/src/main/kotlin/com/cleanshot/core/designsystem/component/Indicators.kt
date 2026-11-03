package com.cleanshot.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Motion
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing

/** Small capsule label (status, privacy level...). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(Radius.pill).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        } else {
            Box(Modifier.size(6.dp).clip(Radius.pill).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Animated circular progress with gradient stroke. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 120.dp, stroke: Dp = 10.dp, content: @Composable () -> Unit = {}) {
    val c = CleanShotTheme.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.EMPHASIZED, easing = Motion.Emphasized), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(c.glassBorder, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            drawArc(
                Brush.sweepGradient(listOf(c.accent, c.accentAlt, c.accent)), -90f, 360f * animated, false, topLeft, arcSize,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Thin gradient progress bar. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val c = CleanShotTheme.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.STANDARD), label = "bar")
    Box(modifier.fillMaxWidth().height(6.dp).clip(Radius.pill).background(c.glassBorder)) {
        Box(
            Modifier.fillMaxWidth(animated).height(6.dp).clip(Radius.pill)
                .background(if (color != null) Brush.horizontalGradient(listOf(color, color)) else Brush.horizontalGradient(listOf(c.accent, c.accentAlt))),
        )
    }
}

/** Numeric highlight tile. */
@Composable
fun MetricTile(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = CleanShotTheme.colors
    GlassCard(modifier, onClick = onClick) {
        Box(Modifier.size(36.dp).clip(Radius.sm).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    val c = CleanShotTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        trailing?.invoke()
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val c = CleanShotTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(0.42f))
        Spacer(Modifier.width(Spacing.sm))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor ?: c.textPrimary, modifier = Modifier.weight(0.58f))
    }
}
