package com.financetracker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.financetracker.ui.navigation.Screen
import com.financetracker.ui.navigation.BottomNavBar
import com.financetracker.ui.navigation.NavGraph
import com.financetracker.ui.theme.FinanceTrackerTheme

@Composable
fun App(initialNavigateToAdd: Boolean = false) {
    FinanceTrackerTheme {
        val navController = rememberNavController()

        LaunchedEffect(Unit) {
            if (initialNavigateToAdd) {
                navController.navigate(Screen.AddTransaction.route)
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    BottomNavBar(
                        currentRoute = currentRoute,
                        onItemClick = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            NavGraph(
                navController = navController,
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            )
        }
    }
}
