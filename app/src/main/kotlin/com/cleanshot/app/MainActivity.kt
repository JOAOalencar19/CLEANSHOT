package com.cleanshot.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.cleanshot.core.designsystem.component.AuroraBackground
import com.cleanshot.core.designsystem.theme.CleanShotTheme
import com.cleanshot.domain.repository.SettingsRepository
import com.cleanshot.domain.repository.ThemeMode
import com.cleanshot.feature.navigation.CleanShotNavHost
import com.cleanshot.feature.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shared = if (savedInstanceState == null) sharedImage(intent) else null
        setContent {
            val userSettings by settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (userSettings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            CleanShotTheme(darkTheme = dark) {
                AuroraBackground {
                    val nav = rememberNavController()
                    LaunchedEffect(shared) { if (shared != null) nav.navigate(Routes.analysis(shared)) }
                    CleanShotNavHost(nav = nav)
                }
            }
        }
    }

    /** Image received through "Share → CleanShot". */
    private fun sharedImage(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        return uri?.toString()
    }
}
