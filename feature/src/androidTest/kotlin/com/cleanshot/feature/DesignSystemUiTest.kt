package com.cleanshot.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanshot.core.designsystem.component.ExpandableSection
import com.cleanshot.core.designsystem.component.PrimaryButton
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignSystemUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun expandableSectionRevealsContent() {
        rule.setContent {
            CleanShotTheme {
                var open by remember { mutableStateOf(false) }
                ExpandableSection("Localização", Icons.Rounded.LocationOn, Color.Red, open, { open = !open }) { Text("Latitude") }
            }
        }
        rule.onNodeWithText("Localização").performClick()
        rule.onNodeWithText("Latitude").assertIsDisplayed()
    }

    @Test fun primaryButtonInvokesAction() {
        var clicks = 0
        rule.setContent { CleanShotTheme { PrimaryButton("Selecionar fotografia", { clicks++ }) } }
        rule.onNodeWithText("Selecionar fotografia").performClick()
        assertEquals(1, clicks)
    }
}
