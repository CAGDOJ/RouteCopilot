package com.routecopilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RouteCopilotColors = lightColorScheme(
    primary = RcPrimary,
    secondary = RcSecondary,
    tertiary = RcAccent,
    background = RcBackground,
    surface = RcSurface,
    surfaceVariant = RcSurfaceSecondary,
    onPrimary = RcOnPrimary,
    onSecondary = RcOnPrimary,
    onTertiary = RcOnPrimary,
    onBackground = RcText,
    onSurface = RcText,
    onSurfaceVariant = RcMuted,
    error = RcDanger,
    outline = RcOutline
)

@Composable
fun RouteCopilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RouteCopilotColors,
        typography = Typography,
        content = content
    )
}
