package com.financetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 1.0.9 UI 规范: 莫兰迪色系 + 中性色
private val LightColorScheme = lightColorScheme(
    primary = Mint500,
    onPrimary = Color.White,
    primaryContainer = Mint500.copy(alpha = 0.12f),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Mist500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9D8),
    onSecondaryContainer = TextPrimary,
    tertiary = Amber500,
    onTertiary = Color.White,
    error = Brick500,
    onError = Color.White,
    errorContainer = Brick500.copy(alpha = 0.12f),
    onErrorContainer = Color(0xFF410002),
    background = PageBg,
    onBackground = TextPrimary,
    surface = Color(0xFFFFFCF6),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF0E9DD),
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color(0xFFFFFCF6),
    surfaceContainerHighest = Color(0xFFF0E9DD),
    surfaceContainer = Color(0xFFF4EDE1),
    surfaceContainerLow = Color(0xFFF9F2E7),
    surfaceContainerHigh = Color(0xFFEFE6D8),
    outline = Color(0xFFB7A996),
    outlineVariant = Divider,
)

@Composable
fun FinanceTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = androidx.compose.material3.Shapes(
            small = CardSmall, medium = CardLarge, large = CardLarge,
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
