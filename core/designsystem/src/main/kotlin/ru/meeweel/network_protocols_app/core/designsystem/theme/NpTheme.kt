package ru.meeweel.network_protocols_app.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalNpColorScheme = staticCompositionLocalOf<NpColorScheme> {
    error("NpColorScheme is not provided.")
}

private val LocalNpTypography = staticCompositionLocalOf<NpTypography> {
    error("NpTypography is not provided.")
}

object NpTheme {
    val colorScheme: NpColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalNpColorScheme.current

    val typography: NpTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalNpTypography.current
}

@Composable
fun NpTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = LightNpColorScheme
    val typography = DefaultNpTypography

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = colorScheme.background,
            surface = colorScheme.surfacePrimary,
            primary = colorScheme.accent,
            onPrimary = colorScheme.textOnAccent,
            onBackground = colorScheme.textPrimary,
            onSurface = colorScheme.textPrimary,
            secondary = colorScheme.accentMuted,
            outline = colorScheme.border,
        ),
    ) {
        CompositionLocalProvider(
            LocalNpColorScheme provides colorScheme,
            LocalNpTypography provides typography,
            content = content,
        )
    }
}
