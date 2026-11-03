package com.cleanshot.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Palette

/** CleanShot mark: a glass tile with an aperture made of six blades and a clean "spark". */
@Composable
fun CleanShotMark(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(
            Brush.linearGradient(listOf(Palette.Aurora, Palette.Iris, Palette.Lumen), Offset.Zero, Offset(s, s)),
            cornerRadius = CornerRadius(s * 0.3f),
        )
        drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(s * 0.04f, s * 0.04f), size = Size(s * 0.92f, s * 0.46f), cornerRadius = CornerRadius(s * 0.26f))
        val c = Offset(s / 2, s / 2)
        val r = s * 0.27f
        drawCircle(Color.White.copy(alpha = 0.95f), r, c, style = Stroke(s * 0.06f))
        for (i in 0 until 6) {
            rotate(i * 60f, c) {
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    lineTo(c.x + r * 0.5f, c.y - r * 0.15f)
                    lineTo(c.x + r * 0.12f, c.y - r * 0.05f)
                    close()
                }
                drawPath(p, Color.White.copy(alpha = 0.9f))
            }
        }
        val spark = Offset(s * 0.77f, s * 0.24f)
        drawCircle(Color.White, s * 0.05f, spark)
        drawCircle(Color.White.copy(alpha = 0.35f), s * 0.1f, spark)
    }
}

@Composable
fun CleanShotWordmark(modifier: Modifier = Modifier, markSize: Dp = 40.dp) {
    val c = CleanShotTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CleanShotMark(markSize)
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.textPrimary, fontWeight = FontWeight.SemiBold)) { append("Clean") }
                withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Light)) { append("Shot") }
            },
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
