package com.cleanshot.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing

/** Primary call to action: luminous gradient pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
) {
    val c = CleanShotTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val colors = if (destructive) listOf(c.danger, c.danger.copy(alpha = 0.8f)) else listOf(c.accent, c.accentAlt)
    Box(
        modifier
            .pressScale(interaction)
            .heightIn(min = 56.dp)
            .shadow(if (enabled) 18.dp else 0.dp, Radius.pill, ambientColor = colors.first(), spotColor = colors.first())
            .clip(Radius.pill)
            .background(Brush.horizontalGradient(colors))
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(interaction, null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(loading, label = "loading") { busy ->
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
            else ButtonContent(text, icon, Color.White)
        }
    }
}

/** Secondary action on glass. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = CleanShotTheme.colors
    GlassSurface(modifier.heightIn(min = 52.dp).alpha(if (enabled) 1f else 0.45f), shape = Radius.pill, onClick = if (enabled) onClick else null) {
        Box(Modifier.align(Alignment.Center).padding(horizontal = Spacing.lg, vertical = 14.dp)) {
            ButtonContent(text, icon, c.textPrimary)
        }
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}
