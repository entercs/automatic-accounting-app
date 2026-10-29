package com.financetracker.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.financetracker.ui.screen.add.AddTransactionScreen
import com.financetracker.ui.screen.detail.TransactionDetailScreen
import com.financetracker.ui.screen.home.HomeScreen
import com.financetracker.ui.screen.search.SearchScreen
import com.financetracker.ui.screen.settings.SettingsScreen
import com.financetracker.ui.screen.settings.AccountingSettingsScreen
import com.financetracker.ui.screen.statistics.StatisticsScreen
import com.financetracker.ui.screen.recognition.*

private const val ANIM_DURATION = 200

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Screen.Home.route, modifier = modifier) {
        composable(
            Screen.Home.route,
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION)) },
        ) {
            HomeScreen(
                onNavigateToAdd = { navController.navigate(Screen.addTransactionRoute()) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onTransactionClick = { id -> navController.navigate(Screen.detailRoute(id)) },
            )
        }
        composable(
            Screen.Statistics.route,
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION)) },
        ) {
            StatisticsScreen(onTransactionClick = { id -> navController.navigate(Screen.detailRoute(id)) })
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onTransactionClick = { id -> navController.navigate(Screen.detailRoute(id)) },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(
            Screen.Settings.route,
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION)) },
        ) {
            SettingsScreen(onNavigateToSettings = { navController.navigate(Screen.Preferences.route) })
        }
        composable(Screen.Preferences.route) {
            AccountingSettingsScreen(onNavigateBack = { navController.popBackStack() }, onOpenRecognition = { navController.navigate(Screen.Recognition.route) })
        }
        composable(Screen.Recognition.route) {
            RecognitionRecordsScreen(
                onBack = { navController.popBackStack() },
                onDetail = { navController.navigate("recognition/$it") },
                onTest = { navController.navigate("notification_test") },
            )
        }
        composable(Screen.RecognitionDetail.route, arguments = listOf(navArgument("recordId") { type = NavType.StringType })) { entry ->
            RecognitionDetailScreen(entry.arguments?.getString("recordId").orEmpty(),
                onBack = { navController.popBackStack() },
                onTest = { navController.navigate("notification_test?sampleId=$it") },
                onManualAdd = { navController.navigate(Screen.addTransactionRoute(sampleId = it)) })
        }
        composable(Screen.NotificationTest.route, arguments = listOf(navArgument("sampleId") { type = NavType.StringType; defaultValue = "" })) { entry ->
            NotificationTestScreen(entry.arguments?.getString("sampleId").orEmpty(), onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.AddTransaction.route,
            arguments = listOf(
                navArgument("editId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("refundId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("sampleId") { type = NavType.StringType; defaultValue = "" },
            ),
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { fadeIn(tween(ANIM_DURATION)) },
            popExitTransition = { fadeOut(tween(ANIM_DURATION)) },
        ) { backStackEntry ->
            val editId = backStackEntry.arguments?.getLong("editId") ?: -1L
            val refundId = backStackEntry.arguments?.getLong("refundId") ?: -1L
            AddTransactionScreen(
                sampleId = backStackEntry.arguments?.getString("sampleId")?.takeIf { it.isNotBlank() },
                editTransactionId = if (editId > 0) editId else null,
                refundTransactionId = if (refundId > 0) refundId else null,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType }),
            enterTransition = { fadeIn(tween(ANIM_DURATION)) },
            exitTransition = { fadeOut(tween(ANIM_DURATION)) },
            popEnterTransition = { fadeIn(tween(ANIM_DURATION)) },
            popExitTransition = { fadeOut(tween(ANIM_DURATION)) },
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: return@composable
            TransactionDetailScreen(
                transactionId = transactionId,
                onNavigateBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Screen.addTransactionRoute(id)) },
                onRefund = { navController.popBackStack() },
            )
        }
    }
}