package ru.meeweel.network_protocols_app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

data class NpColorScheme(
    val background: Color,
    val surfacePrimary: Color,
    val surfaceSecondary: Color,
    val accent: Color,
    val accentMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnAccent: Color,
    val border: Color,
    val success: Color,
)

internal val LightNpColorScheme = NpColorScheme(
    background = Color(0xFFF5F2EA),
    surfacePrimary = Color(0xFFFFFCF6),
    surfaceSecondary = Color(0xFFE7F0E9),
    accent = Color(0xFF1E6B4D),
    accentMuted = Color(0xFFBFD7C8),
    textPrimary = Color(0xFF1A241D),
    textSecondary = Color(0xFF5B665F),
    textOnAccent = Color(0xFFFFFFFF),
    border = Color(0xFFD5DCD4),
    success = Color(0xFF2F8F64),
)
