package com.example.zen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zen.persona.LocalPersona
import com.example.zen.persona.LocalPersonaColors

/**
 * The active persona's [PersonaMark] inside a flat accent-tinted circle. The one component that
 * renders the persona's identity at a fixed size — no emoji anywhere in the product.
 */
@Composable
fun PersonaSigil(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    glyphSize: Dp = 22.dp
) {
    val c = LocalPersonaColors.current
    val persona = LocalPersona.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(c.accent.copy(alpha = if (c.isLight) 0.08f else 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        PersonaMark(
            persona = persona,
            size = glyphSize,
            strokeWidth = if (glyphSize > 30.dp) 2.5.dp else 2.dp
        )
    }
}
