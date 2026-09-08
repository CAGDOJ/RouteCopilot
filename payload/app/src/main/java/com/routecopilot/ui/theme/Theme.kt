package com.routecopilot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RouteCopilotColors = darkColorScheme(
    primary = RcPrimary,
    secondary = RcSecondary,
    tertiary = RcAccent,
    background = RcBackground,
    surface = RcSurface,
    onPrimary = RcText,
    onSecondary = RcText,
    onTertiary = RcText,
    onBackground = RcText,
    onSurface = RcText
)

@Composable
fun RouteCopilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RouteCopilotColors,
        typography = Typography,
        content = content
    )
}
