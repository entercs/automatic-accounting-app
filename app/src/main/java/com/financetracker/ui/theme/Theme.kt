package com.financetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 1.0.9 UI 规范: 莫兰迪色系 + 中性色
private val LightColorScheme = lightColorScheme(
    primary = Mint500,
    onPrimary = Color.White,
    primaryContainer = Mint500.copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Mist500,
    onSecondary = Color.White,
    secondaryContainer = Mist500.copy(alpha = 0.12f),
    onSecondaryContainer = Color(0xFF001D36),
    tertiary = Amber500,
    onTertiary = Color.White,
    error = Brick500,
    onError = Color.White,
    errorContainer = Brick500.copy(alpha = 0.12f),
    onErrorContainer = Color(0xFF410002),
    background = PageBg,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    outlineVariant = Divider,
)

@Composable
fun FinanceTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
