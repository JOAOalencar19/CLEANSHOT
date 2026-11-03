package com.cleanshot.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing scale (4 pt grid). */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    /** Horizontal page gutter. */
    val gutter = 20.dp
}

object Radius {
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(20.dp)
    val lg = RoundedCornerShape(28.dp)
    val xl = RoundedCornerShape(36.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Motion tokens: durations in ms and easing curves. */
object Motion {
    const val QUICK = 140
    const val STANDARD = 280
    const val EMPHASIZED = 450
    const val AMBIENT = 14_000
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard: Easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
    const val PRESSED_SCALE = 0.965f
}
