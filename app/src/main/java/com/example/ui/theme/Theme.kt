package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class DashboardTheme {
    CYBER_NEON,
    CARBON_GOLD,
    MATRIX_GREEN,
    DAY_LIGHT
}

// 4. Day Light Scheme (high-contrast sunlight readable)
private val DayLightColorScheme = lightColorScheme(
    primary = Color(0xFF0F172A), // Slate 900 -> Primary Text / Needle #0F172A
    onPrimary = Color.White,
    secondary = Color(0xFF475569), // Secondary Text #475569
    onSecondary = Color.White,
    tertiary = Color(0xFFFF5A36), // Accent / Action Button #FF5A36
    onTertiary = Color.White,
    background = Color(0xFFF4F6F9), // Main Background #F4F6F9
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF4F6F9), // Large console cards background and app background are #F4F6F9
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0), // Card Borders/Gauges #E2E8F0
    onSurfaceVariant = Color(0xFF475569), // Secondary Text #475569
)

// 1. Cyber Neon Scheme
private val CyberNeonColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color.Black,
    secondary = NeonPurple,
    onSecondary = Color.White,
    tertiary = CyberSecondary,
    background = CyberBlack,
    onBackground = Color.White,
    surface = Slate900,
    onSurface = Color.White,
    surfaceVariant = Slate800,
    onSurfaceVariant = ElectricCyan,
)

// 2. Carbon Gold Scheme
private val CarbonGoldColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = Color.Black,
    secondary = GoldSecondary,
    onSecondary = Color.Black,
    tertiary = GoldAccent,
    background = CyberBlack,
    onBackground = Color.White,
    surface = Color(0xFF1E1A12),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2E281F),
    onSurfaceVariant = GoldAccent,
)

// 3. Matrix Green Scheme
private val MatrixGreenColorScheme = darkColorScheme(
    primary = MatrixGreenAccent,
    onPrimary = Color.Black,
    secondary = MatrixSecondary,
    onSecondary = Color.White,
    tertiary = MatrixGreenAccent,
    background = CyberBlack,
    onBackground = Color.White,
    surface = MatrixCard,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0F1E14),
    onSurfaceVariant = MatrixGreenAccent,
)

@Composable
fun DRouteTheme(
    theme: DashboardTheme = DashboardTheme.CYBER_NEON,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        DashboardTheme.CYBER_NEON -> CyberNeonColorScheme
        DashboardTheme.CARBON_GOLD -> CarbonGoldColorScheme
        DashboardTheme.MATRIX_GREEN -> MatrixGreenColorScheme
        DashboardTheme.DAY_LIGHT -> DayLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
