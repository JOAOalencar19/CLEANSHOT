package com.cleanshot.feature.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cleanshot.core.designsystem.theme.Motion
import com.cleanshot.feature.analysis.AnalysisScreen
import com.cleanshot.feature.batch.BatchScreen
import com.cleanshot.feature.cleaner.CleanerScreen
import com.cleanshot.feature.export.ExportScreen
import com.cleanshot.feature.history.HistoryScreen
import com.cleanshot.feature.home.HomeScreen
import com.cleanshot.feature.provenance.ProvenanceScreen
import com.cleanshot.feature.report.CompareScreen
import com.cleanshot.feature.report.ReportScreen
import com.cleanshot.feature.settings.SettingsScreen

@Composable
fun CleanShotNavHost(modifier: Modifier = Modifier, nav: NavHostController = rememberNavController()) {
    val sid = listOf(navArgument("sid") { type = NavType.StringType })
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { slideInHorizontally(tween(Motion.EMPHASIZED, easing = Motion.Emphasized)) { it / 5 } + fadeIn(tween(Motion.STANDARD)) },
        exitTransition = { scaleOut(tween(Motion.EMPHASIZED), targetScale = 0.96f) + fadeOut(tween(Motion.STANDARD)) },
        popEnterTransition = { scaleIn(tween(Motion.EMPHASIZED), initialScale = 0.96f) + fadeIn(tween(Motion.STANDARD)) },
        popExitTransition = { slideOutHorizontally(tween(Motion.EMPHASIZED, easing = Motion.Emphasized)) { it / 5 } + fadeOut(tween(Motion.QUICK)) },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onAnalyze = { nav.navigate(Routes.analysis(it)) },
                onAnalyzeThenClean = { nav.navigate(Routes.analysis(it, next = "clean")) },
                onBatch = { nav.navigate(Routes.BATCH) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.ANALYSIS,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType; defaultValue = "" },
                navArgument("next") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            AnalysisScreen(
                onBack = { nav.popBackStack() },
                onClean = { nav.navigate(Routes.cleaner(it)) },
                onProvenance = { nav.navigate(Routes.provenance(it)) },
            )
        }
        composable(Routes.CLEANER, arguments = sid) {
            CleanerScreen(onBack = { nav.popBackStack() }, onDone = { id -> nav.navigate(Routes.report(id)) { popUpTo(Routes.CLEANER) { inclusive = true } } })
        }
        composable(Routes.REPORT, arguments = sid) {
            ReportScreen(onBack = { nav.popBackStack() }, onExport = { nav.navigate(Routes.export(it)) }, onCompare = { nav.navigate(Routes.compare(it)) })
        }
        composable(Routes.COMPARE, arguments = sid) { CompareScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PROVENANCE, arguments = sid) { ProvenanceScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.EXPORT, arguments = sid) {
            ExportScreen(onBack = { nav.popBackStack() }, onFinish = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.BATCH) { BatchScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
