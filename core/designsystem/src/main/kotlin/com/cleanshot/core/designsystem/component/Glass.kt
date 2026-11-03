package com.cleanshot.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Motion
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient backdrop: slow-moving luminous orbs behind every screen. Glass surfaces placed on top
 * pick up the colour through their translucency, giving a "liquid glass" depth without the cost
 * of a real-time backdrop blur.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = CleanShotTheme.colors
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(Motion.AMBIENT, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            .drawBehind {
                val w = size.width; val h = size.height; val r = maxOf(w, h)
                fun orb(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
                    brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = radius),
                    radius = radius, center = Offset(cx, cy),
                )
                orb(c.orbA, w * (0.15f + 0.08f * cos(phase)), h * (0.08f + 0.04f * sin(phase)), r * 0.62f)
                orb(c.orbB, w * (0.95f + 0.06f * sin(phase * 0.7f)), h * (0.32f + 0.05f * cos(phase)), r * 0.55f)
                orb(c.orbC, w * (0.35f + 0.10f * sin(phase)), h * (1.02f + 0.03f * cos(phase * 1.3f)), r * 0.6f)
            },
        content = content,
    )
}

/** Press-to-scale micro-interaction shared by all tappable surfaces. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) Motion.PRESSED_SCALE else 1f, tween(Motion.QUICK, easing = Motion.Standard), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Translucent "glass" surface with a light-catching edge. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.lg,
    onClick: (() -> Unit)? = null,
    tint: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = CleanShotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    var m = modifier
    if (onClick != null) m = m.pressScale(interaction)
    m = m
        .clip(shape)
        .background(Brush.verticalGradient(listOf(c.glassTop, c.glassBottom)))
        .background(tint)
        .border(1.dp, Brush.linearGradient(listOf(c.glassHighlight.copy(alpha = if (c.isDark) 0.22f else 0.9f), c.glassBorder, c.glassBorder)), shape)
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
    Box(m, content = content)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    tint: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, onClick = onClick, tint = tint) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}
