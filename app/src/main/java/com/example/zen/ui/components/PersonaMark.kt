package com.example.zen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.persona.Persona

/**
 * The persona brand marks: four Canvas-drawn monoline glyphs sharing one stroke weight, cap
 * style, and bounding geometry — so they read as one family by construction. Replaces emoji
 * everywhere a persona is represented.
 *
 * Drawn from a normalized `Path` in a unit box, which makes the stroke animatable: [progress]
 * trims the path (0 → 1) so the mark can "draw itself in" on the friction screen.
 *
 *  - GOBLIN — a jagged bolt: mischief, interruption.
 *  - COACH  — double ascending chevron: reps, upward push.
 *  - ZEN    — an ensō: a single open circular arc, incomplete on purpose.
 *  - SAGE   — a square inward spiral: a scroll seen end-on; accumulated text, patience.
 */
@Composable
fun PersonaMark(
    persona: Persona,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = LocalPersonaColors.current.accent,
    progress: Float = 1f
) {
    val path = remember(persona) { markPath(persona) }
    val measure = remember(persona) { PathMeasure().apply { setPath(path, false) } }

    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        // Paths are authored in a 1×1 unit box; scale up to the canvas.
        scale(scaleX = this.size.width, scaleY = this.size.height, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val toDraw = if (progress >= 1f) {
                path
            } else {
                Path().also { partial ->
                    measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), partial, true)
                }
            }
            drawPath(
                path = toDraw,
                color = color,
                style = Stroke(
                    // Stroke width must be expressed in unit-box space after scaling.
                    width = stroke.width / this.size.width.coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun markPath(persona: Persona): Path = when (persona) {
    Persona.GOBLIN -> Path().apply {
        // Jagged bolt.
        moveTo(0.64f, 0.08f)
        lineTo(0.34f, 0.52f)
        lineTo(0.56f, 0.52f)
        lineTo(0.36f, 0.92f)
    }

    Persona.COACH -> Path().apply {
        // Double ascending chevron.
        moveTo(0.24f, 0.56f)
        lineTo(0.50f, 0.30f)
        lineTo(0.76f, 0.56f)
        moveTo(0.24f, 0.82f)
        lineTo(0.50f, 0.56f)
        lineTo(0.76f, 0.82f)
    }

    Persona.ZEN -> Path().apply {
        // Ensō: ~300° arc, gap at the upper right. Inset so the round caps stay in-box.
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(0.12f, 0.12f, 0.88f, 0.88f),
            startAngleDegrees = -50f,
            sweepAngleDegrees = 300f,
            forceMoveTo = true
        )
    }

    Persona.SAGE -> Path().apply {
        // Square inward spiral.
        moveTo(0.16f, 0.20f)
        lineTo(0.84f, 0.20f)
        lineTo(0.84f, 0.84f)
        lineTo(0.28f, 0.84f)
        lineTo(0.28f, 0.42f)
        lineTo(0.64f, 0.42f)
        lineTo(0.64f, 0.64f)
        lineTo(0.46f, 0.64f)
    }
}
