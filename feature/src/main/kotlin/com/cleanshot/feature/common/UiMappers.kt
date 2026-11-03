package com.cleanshot.feature.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Code
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.core.designsystem.theme.Palette as P
import com.cleanshot.domain.model.CheckState
import com.cleanshot.domain.model.FindingStatus
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.PrivacyLevel
import com.cleanshot.domain.model.ProvenanceVerdict
import com.cleanshot.domain.model.RemovalPolicy

fun MetadataCategory.icon(): ImageVector = when (this) {
    MetadataCategory.FILE -> Icons.Rounded.Image
    MetadataCategory.LOCATION -> Icons.Rounded.LocationOn
    MetadataCategory.AUTHORSHIP -> Icons.Rounded.Person
    MetadataCategory.DEVICE -> Icons.Rounded.CameraAlt
    MetadataCategory.CAPTURE -> Icons.Rounded.Tune
    MetadataCategory.DATES -> Icons.Rounded.Schedule
    MetadataCategory.DESCRIPTION -> Icons.Rounded.Description
    MetadataCategory.SOFTWARE -> Icons.Rounded.Code
    MetadataCategory.PROVENANCE -> Icons.Rounded.AutoAwesome
    MetadataCategory.COLOR -> Icons.Rounded.Palette
    MetadataCategory.OTHER -> Icons.Rounded.MoreHoriz
}

@Composable
fun MetadataCategory.tint(): Color = when (this) {
    MetadataCategory.LOCATION -> CleanShotTheme.colors.privacyHigh
    MetadataCategory.AUTHORSHIP -> P.Iris
    MetadataCategory.DEVICE -> CleanShotTheme.colors.privacyMedium
    MetadataCategory.PROVENANCE -> CleanShotTheme.colors.accentAlt
    MetadataCategory.DATES -> CleanShotTheme.colors.info
    MetadataCategory.COLOR -> P.Lumen
    else -> CleanShotTheme.colors.accent
}

@Composable
fun PrivacyLevel.color(): Color = when (this) {
    PrivacyLevel.HIGH -> CleanShotTheme.colors.privacyHigh
    PrivacyLevel.MEDIUM -> CleanShotTheme.colors.privacyMedium
    PrivacyLevel.LOW -> CleanShotTheme.colors.privacyLow
    PrivacyLevel.NONE -> CleanShotTheme.colors.privacyNone
}

@Composable
fun FindingStatus.color(): Color = when (this) {
    FindingStatus.FOUND -> CleanShotTheme.colors.accent
    FindingStatus.NOT_FOUND -> CleanShotTheme.colors.textTertiary
    FindingStatus.UNSUPPORTED -> CleanShotTheme.colors.privacyNone
    FindingStatus.UNVERIFIABLE -> CleanShotTheme.colors.warning
}

@Composable
fun RemovalPolicy.color(): Color = when (this) {
    RemovalPolicy.REMOVABLE -> CleanShotTheme.colors.accent
    RemovalPolicy.PROTECTED_PROVENANCE -> CleanShotTheme.colors.accentAlt
    RemovalPolicy.PROTECTED_RENDERING -> CleanShotTheme.colors.info
    RemovalPolicy.NOT_SUPPORTED -> CleanShotTheme.colors.warning
    RemovalPolicy.NOT_APPLICABLE -> CleanShotTheme.colors.textTertiary
}

fun ProvenanceVerdict.icon(): ImageVector = when (this) {
    ProvenanceVerdict.CREDENTIALS_VERIFIED -> Icons.Rounded.VerifiedUser
    ProvenanceVerdict.CREDENTIALS_FOUND -> Icons.Rounded.GppGood
    ProvenanceVerdict.CREDENTIALS_INVALID -> Icons.Rounded.GppBad
    ProvenanceVerdict.DECLARED_ORIGIN -> Icons.Rounded.Layers
    ProvenanceVerdict.INCONCLUSIVE -> Icons.Rounded.GppMaybe
    ProvenanceVerdict.NO_CREDENTIALS -> Icons.Rounded.Shield
}

@Composable
fun ProvenanceVerdict.color(): Color = when (this) {
    ProvenanceVerdict.CREDENTIALS_VERIFIED -> CleanShotTheme.colors.success
    ProvenanceVerdict.CREDENTIALS_FOUND -> CleanShotTheme.colors.accent
    ProvenanceVerdict.CREDENTIALS_INVALID -> CleanShotTheme.colors.danger
    ProvenanceVerdict.DECLARED_ORIGIN -> P.Iris
    ProvenanceVerdict.INCONCLUSIVE -> CleanShotTheme.colors.warning
    ProvenanceVerdict.NO_CREDENTIALS -> CleanShotTheme.colors.privacyNone
}

@Composable
fun CheckState.color(): Color = when (this) {
    CheckState.PASSED -> CleanShotTheme.colors.success
    CheckState.FAILED -> CleanShotTheme.colors.danger
    CheckState.NOT_CHECKED -> CleanShotTheme.colors.warning
    CheckState.NOT_APPLICABLE -> CleanShotTheme.colors.textTertiary
}

