package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Default colors
val DarkGrey = Color(0xFF1E1E1E)
val LightGrey = Color(0xFFC4C4C4)

// Slate & High-Density Colors
val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate500 = Color(0xFF64748B)

// 1. Cyber Neon Theme Color Palette
val CyberBlack = Color(0xFF000000)
val ElectricCyan = Color(0xFF00F2FE)
val NeonPurple = Color(0xFF9B51E0)
val CyberSecondary = Color(0xFF4FACFE)

// 2. Carbon Gold Theme Color Palette
val CarbonGrey = Color(0xFF131313)
val CarbonCard = Color(0xFF1F1F1F)
val GoldAccent = Color(0xFFFFB300)
val GoldSecondary = Color(0xFFFFD54F)

// 3. Matrix Green Theme Color Palette
val MatrixBlack = Color(0xFF070908)
val MatrixCard = Color(0xFF111412)
val MatrixGreenAccent = Color(0xFF00FF66)
val MatrixSecondary = Color(0xFF10B981)

// Dynamic Speed Interval Threshold Colors
val SpeedEcoGreen = Color(0xFF4CAF50)      // 0 - 20 km/h: Eco Green
val SpeedCityBlue = Color(0xFF2196F3)      // 21 - 40 km/h: City Blue
val SpeedCruisingCyan = Color(0xFF00E5FF)  // 41 - 60 km/h: Cruising Cyan
val SpeedVividYellow = Color(0xFFFFEB3B)   // 61 - 80 km/h: Vivid Yellow
val SpeedWarningOrange = Color(0xFFFF9800) // 81 - 100 km/h: Warning Orange
val SpeedDangerRed = Color(0xFFFF3333)     // 101 - 120 km/h: Danger Red

// Helper to resolve speed based dynamic color intervals
fun getSpeedColor(speedKmh: Double): Color {
    return when {
        speedKmh <= 20.0 -> SpeedEcoGreen
        speedKmh <= 40.0 -> SpeedCityBlue
        speedKmh <= 60.0 -> SpeedCruisingCyan
        speedKmh <= 80.0 -> SpeedVividYellow
        speedKmh <= 100.0 -> SpeedWarningOrange
        else -> SpeedDangerRed
    }
}
