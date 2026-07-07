package com.example.zen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.zen.persona.LocalPersonaColors
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The one backdrop for every screen (and the intervention overlay): a solid persona surface with
 * a single static radial accent tint anchored top-center — a tonal vignette, not a gradient wash,
 * and never animated. Owns the [LocalHazeState] that [GlassCard]s frost against, collapsing the
 * per-screen gradient + haze scaffold into one call.
 */
@Composable
fun PersonaBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val c = LocalPersonaColors.current
    val hazeState = rememberHazeState()

    val surface = c.gradient.first()
    val tintAlpha = if (c.isLight) 0.04f else 0.05f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(c.accent.copy(alpha = tintAlpha), Color.Transparent),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.width * 1.2f
                    )
                )
            }
            .hazeSource(hazeState)
    ) {
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}
