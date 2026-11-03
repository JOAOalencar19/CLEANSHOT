package com.cleanshot.feature.common

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.cleanshot.core.designsystem.component.GlassCard
import com.cleanshot.core.designsystem.component.PrimaryButton
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Radius
import com.cleanshot.core.designsystem.theme.Spacing
import java.io.File

/** Persists access to a picked URI when the provider allows it (Photo Picker and SAF URIs do). */
fun persistUriPermission(context: android.content.Context, uri: Uri, write: Boolean = false) {
    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (write) android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
}

/** True on tablets / unfolded foldables / landscape large screens. */
@Composable
fun isExpandedWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 840

@Composable
fun isMediumWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** Local thumbnail (content Uri or private File). Never loads from the network. */
@Composable
fun ImageThumb(model: Any?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val c = CleanShotTheme.colors
    val data = when (model) {
        is String -> Uri.parse(model)
        is File, is Uri -> model
        else -> null
    }
    SubcomposeAsyncImage(
        model = data,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
        loading = { Box(Modifier.fillMaxSize().background(c.glassTop)) },
        error = {
            Box(Modifier.fillMaxSize().background(c.glassTop), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Image, null, tint = c.textTertiary)
            }
        },
    )
}

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    val c = CleanShotTheme.colors
    Column(modifier.fillMaxSize().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        CircularProgressIndicator(color = c.accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(Spacing.md))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorState(title: String, message: String, onAction: (() -> Unit)?, actionLabel: String = "Voltar", modifier: Modifier = Modifier) {
    val c = CleanShotTheme.colors
    Column(modifier.fillMaxSize().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Box(Modifier.size(72.dp).clip(Radius.pill).background(c.danger.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = c.danger, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        if (onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

/** Card listing warnings / notes. */
@Composable
fun NoticeCard(title: String, lines: List<String>, modifier: Modifier = Modifier, danger: Boolean = false) {
    if (lines.isEmpty()) return
    val c = CleanShotTheme.colors
    val tint = if (danger) c.danger else c.warning
    GlassCard(modifier.fillMaxWidth(), tint = tint.copy(alpha = 0.06f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        }
        lines.forEach {
            Spacer(Modifier.height(6.dp))
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = CleanShotTheme.colors
    Box(
        modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, c.background.copy(alpha = 0.92f), c.background)))
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
    ) { content() }
}
