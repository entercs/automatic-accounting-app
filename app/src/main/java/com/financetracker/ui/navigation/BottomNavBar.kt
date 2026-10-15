package com.financetracker.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier, containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        Screen.bottomNavItems.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = { onItemClick(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
            )
        }
    }
}
