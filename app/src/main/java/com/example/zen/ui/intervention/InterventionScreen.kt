package com.example.zen.ui.intervention

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zen.InterventionRequest
import com.example.zen.persona.LineLibrary
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.components.PersonaBackdrop
import com.example.zen.ui.components.PersonaMark
import com.example.zen.ui.components.PrimaryButton
import com.example.zen.ui.design.ZenSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The friction screen — the most-seen surface in the product. It stays up until the user acts:
 *
 *  - **Leave** (button or back key): the service navigates out *underneath* the overlay while a
 *    short affirmation covers the transition, then dismisses. Never a flash, never a loop.
 *  - **Hold to continue**: a deliberate, escalating physical cost (3s/5s/8s by tier). Release
 *    aborts. Completion grants a timed pass via the service.
 *
 * Humor lives in the persona [InterventionRequest.line]; everything else on the canvas is calm,
 * factual, and identical across personas.
 */
@Composable
fun InterventionScreen(
    request: InterventionRequest,
    onLeave: () -> Unit,
    onOverrideCompleted: () -> Unit
) {
    val c = LocalPersonaColors.current
    var phase by remember { mutableStateOf(Phase.INTERVENING) }
    val scope = rememberCoroutineScope()

    // Composed entrance: fade + small slide. Nothing bounces.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "interventionEnter"
    )

    PersonaBackdrop {
        Crossfade(targetState = phase, animationSpec = tween(250), label = "interventionPhase") { p ->
            when (p) {
                Phase.INTERVENING -> InterveningContent(
                    request = request,
                    enter = enter,
                    onLeave = {
                        phase = Phase.LEAVING
                        onLeave()
                    },
                    onOverrideHeld = {
                        phase = Phase.GRANTED
                        scope.launch {
                            delay(600)
                            onOverrideCompleted()
                        }
                    }
                )

                Phase.LEAVING -> CenteredLine(LineLibrary.leaveAffirmation(request.persona))
                Phase.GRANTED -> CenteredLine(LineLibrary.overrideGranted(request.persona))
            }
        }
    }
}

private enum class Phase { INTERVENING, LEAVING, GRANTED }

@Composable
private fun InterveningContent(
    request: InterventionRequest,
    enter: Float,
    onLeave: () -> Unit,
    onOverrideHeld: () -> Unit
) {
    val c = LocalPersonaColors.current

    // The mark draws itself in over 600ms, starting shortly after the screen lands.
    var markShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        markShown = true
    }
    val markProgress by animateFloatAsState(
        targetValue = if (markShown) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "markDrawIn"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = ZenSpacing.screenGutter)
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * 16.dp.toPx()
            },
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(96.dp))

        PersonaMark(
            persona = request.persona,
            size = 64.dp,
            strokeWidth = 3.dp,
            progress = markProgress
        )

        Spacer(Modifier.height(ZenSpacing.xl))

        // Factual kicker — data, not jokes. This line makes the screen an instrument, not a gag.
        Text(
            text = kickerText(request),
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary
        )

        Spacer(Modifier.height(ZenSpacing.md))

        // The persona's line — the one place humor lives on this screen.
        Text(
            text = request.line,
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary
        )

        request.minutesInAppToday?.takeIf { it > 0 }?.let { minutes ->
            Spacer(Modifier.height(ZenSpacing.lg))
            Text(
                text = "You've spent $minutes minutes here today.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Leave ${request.appName}",
            onClick = onLeave,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(ZenSpacing.md))

        HoldToContinueRow(
            persona = request,
            onHeld = onOverrideHeld
        )

        Spacer(Modifier.height(ZenSpacing.xl))
    }
}

/**
 * The quiet override affordance: press and hold for an escalating duration. Progress is a thin
 * ring beside the label; releasing early springs it back to zero.
 */
@Composable
private fun HoldToContinueRow(
    persona: InterventionRequest,
    onHeld: () -> Unit
) {
    val c = LocalPersonaColors.current
    val holdMillis = remember(persona.tier) { holdDurationMs(persona.tier) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var holding by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pointerInput(holdMillis) {
                detectTapGestures(
                    onPress = {
                        if (completed) return@detectTapGestures
                        holding = true
                        // Fill the ring over the remaining duration; fire the moment it completes.
                        val fill = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = (holdMillis * (1f - progress.value)).toInt(),
                                    easing = LinearEasing
                                )
                            )
                            if (progress.value >= 1f && !completed) {
                                completed = true
                                onHeld()
                            }
                        }
                        tryAwaitRelease()
                        holding = false
                        if (!completed) {
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, spring()) }
                        }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HoldProgressRing(
            progress = progress.value,
            color = c.textSecondary,
            activeColor = c.accent
        )

        Spacer(Modifier.padding(start = ZenSpacing.sm))

        Text(
            text = if (holding) LineLibrary.overrideAside(persona.persona) else "Hold to continue anyway",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier
                .padding(start = ZenSpacing.sm)
                .alpha(if (holding) 1f else 0.85f)
        )
    }
}

@Composable
private fun HoldProgressRing(progress: Float, color: Color, activeColor: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            color = color.copy(alpha = 0.25f),
            style = stroke,
            radius = size.minDimension / 2f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
        if (progress > 0f) {
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = stroke
            )
        }
    }
}

@Composable
private fun CenteredLine(text: String) {
    val c = LocalPersonaColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = ZenSpacing.xl)
        )
    }
}

private fun kickerText(request: InterventionRequest): String {
    val ordinal = ordinal(request.interceptionOrdinal)
    return "${request.appName} · $ordinal interception today".uppercase()
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

/** Escalating physical cost: the day's relapses make overriding progressively harder. */
private fun holdDurationMs(tier: Int): Int = when (tier) {
    0 -> 3_000
    1 -> 5_000
    else -> 8_000
}
