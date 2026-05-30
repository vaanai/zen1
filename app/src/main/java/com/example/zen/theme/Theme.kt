package com.example.zen.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CosmicColorScheme = darkColorScheme(
    primary = NeonViolet,
    secondary = CyberCyan,
    tertiary = SlashedRed,
    background = DeepSpaceBlack,
    surface = CosmicNavy,
    onPrimary = TextPrimary,
    onSecondary = DeepSpaceBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ZenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // We enforce the gorgeous Cosmic theme for that WOW effect!
    MaterialTheme(
        colorScheme = CosmicColorScheme,
        typography = Typography,
        content = content
    )
}
