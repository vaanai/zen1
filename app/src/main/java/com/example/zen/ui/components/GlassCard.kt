package com.example.zen.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.design.ZenElevation
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The [HazeState] that backing surfaces frost against. A screen sets this at its root (behind the
 * gradient); [GlassCard] reads it to blur the content beneath. Null → no blur source available, so
 * cards fall back to a plain translucent fill.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * The single card primitive for the app: a frosted-glass surface over the persona gradient with a
 * top-lit hairline border. Replaces the ad-hoc `Card + border` pattern. On devices without blur
 * support (API < 31) Haze draws the persona's translucent `cardBackground` scrim instead — matching
 * the app's previous look.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ZenRadius.card,
    contentPadding: Dp = ZenSpacing.cardPadding,
    pressed: Boolean = false,
    content: @Composable () -> Unit
) {
    val c = LocalPersonaColors.current
    val haze = LocalHazeState.current
    // 1.5% press shrink: tactile without being cartoonish on a full-width card.
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "cardPress")

    val glass = Modifier
        .scale(scale)
        .then(
            if (c.isLight) {
                // Light personas separate from the parchment with a soft ambient shadow.
                Modifier.shadow(
                    elevation = ZenElevation.ambient / 2,
                    shape = shape,
                    ambientColor = c.textPrimary.copy(alpha = 0.35f),
                    spotColor = c.textPrimary.copy(alpha = 0.25f)
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .then(
            if (haze != null) {
                Modifier.hazeEffect(
                    state = haze,
                    style = HazeStyle(
                        tints = listOf(HazeTint(c.cardBackground)),
                        blurRadius = 22.dp,
                        noiseFactor = 0f,
                        fallbackTint = HazeTint(c.cardBackground)
                    )
                )
            } else {
                Modifier.background(c.cardBackground)
            }
        )
        .border(ZenElevation.hairline, cardBorder(), shape)

    Box(modifier = modifier.then(glass)) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * Card edge treatment. Dark personas get a subtle top-lit rim (reads as glass under light);
 * light personas get a flat hairline — a bright rim on parchment reads as a rendering artifact.
 */
@Composable
private fun cardBorder(): Brush {
    val c = LocalPersonaColors.current
    return if (c.isLight) {
        Brush.verticalGradient(listOf(c.cardBorder, c.cardBorder))
    } else {
        Brush.verticalGradient(
            listOf(
                c.textPrimary.copy(alpha = 0.14f),
                c.cardBorder
            )
        )
    }
}
