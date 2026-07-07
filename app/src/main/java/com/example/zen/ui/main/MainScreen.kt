package com.example.zen.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zen.data.AppUsageItem
import com.example.zen.data.ZenPrefs
import com.example.zen.persona.LineLibrary
import com.example.zen.persona.LocalPersona
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.components.GlassCard
import com.example.zen.ui.components.PersonaSigil
import com.example.zen.ui.components.SectionHeader
import com.example.zen.ui.components.StatChip
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel,
    prefs: ZenPrefs,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val persona = LocalPersona.current

    // Backdrop is provided by HomeScaffold; this page is just content.
    LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = ZenSpacing.screenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = ZenSpacing.xl, bottom = ZenSpacing.xxl)
        ) {
            item {
                HeaderRow(
                    personaName = persona.displayName,
                    protectionOn = uiState.isAccessibilityEnabled,
                    onOpenSettings = onOpenAbout
                )
            }

            item {
                HeroRing(
                    saves = uiState.savesToday,
                    spentMinutes = uiState.totalTimeSpentMinutes,
                    capMinutes = uiState.dailyCapMinutes,
                    savesLabel = LineLibrary.savesLabel(persona)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ZenSpacing.sm, bottom = ZenSpacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(ZenSpacing.md)
                ) {
                    StatChip(
                        value = "${uiState.streakDays}",
                        label = "Day streak",
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        value = "${uiState.totalTimeSpentMinutes}m",
                        label = "Today / ${uiState.dailyCapMinutes}m",
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        value = "${uiState.savesTotal}",
                        label = "All-time",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { ShieldCard(persona) }

            // A finished setup renders nothing here — the dashboard shouldn't nag when all is
            // well. Status lives in the header line; controls live in system settings.
            if (!uiState.isAccessibilityEnabled || !uiState.isUsageAccessEnabled) {
                item {
                    SetupCard(
                        accessibilityGranted = uiState.isAccessibilityEnabled,
                        usageGranted = uiState.isUsageAccessEnabled,
                        onAccessibility = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onUsage = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    )
                }
            }

            if (uiState.isUsageAccessEnabled && uiState.appStatsList.isNotEmpty()) {
                item { SectionHeader("Screen time", Modifier.padding(top = ZenSpacing.sectionGap)) }
                items(uiState.appStatsList) { app ->
                    AppStatsCard(app = app, capMinutes = uiState.dailyCapMinutes)
                    Spacer(Modifier.height(ZenSpacing.sm))
                }
            }

            item {
                SectionHeader("Goal", Modifier.padding(top = ZenSpacing.sectionGap))
                DailyGoalCard(prefs = prefs, initialCap = uiState.dailyCapMinutes)
            }
        }
}

/** The daily screen-time goal the hero ring measures against. */
@Composable
private fun DailyGoalCard(prefs: ZenPrefs, initialCap: Int) {
    val c = LocalPersonaColors.current
    var cap by remember { mutableStateOf(initialCap.toFloat()) }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Daily screen-time goal: ${cap.toInt()} min",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary
            )
            Slider(
                value = cap,
                onValueChange = { cap = it; prefs.dailyCapMinutes = it.toInt() },
                valueRange = 15f..240f,
                steps = 14,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent)
            )
        }
    }
}

@Composable
private fun HeaderRow(personaName: String, protectionOn: Boolean, onOpenSettings: () -> Unit) {
    val c = LocalPersonaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ZenSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Zen",
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary
            )
            Spacer(Modifier.height(ZenSpacing.xs))
            Text(
                text = if (protectionOn) "PROTECTED · ${personaName.uppercase()}" else "PROTECTION OFF",
                style = MaterialTheme.typography.labelMedium,
                color = if (protectionOn) c.accent else c.warn
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = c.textSecondary)
        }
    }
}

/** Animated hero: a flat accent arc eases toward the cap and the saves number counts up. */
@Composable
private fun HeroRing(saves: Int, spentMinutes: Long, capMinutes: Int, savesLabel: String) {
    val c = LocalPersonaColors.current

    val limit = capMinutes.toFloat().coerceAtLeast(1f)
    val target = (spentMinutes / limit).coerceIn(0f, 1f)
    val overCap = spentMinutes >= capMinutes
    val sweepFraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ringSweep"
    )
    val animatedSaves by animateIntAsState(saves, tween(700), label = "savesCount")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(232.dp)
            .padding(ZenSpacing.lg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = c.textPrimary.copy(alpha = 0.08f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = if (overCap) c.danger else c.accent,
                startAngle = -90f,
                sweepAngle = (sweepFraction * 360f).coerceAtLeast(2f),
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$animatedSaves",
                style = MaterialTheme.typography.displayLarge,
                color = c.textPrimary
            )
            Text(
                text = savesLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary
            )
        }
    }
}

@Composable
private fun ShieldCard(persona: com.example.zen.persona.Persona) {
    val c = LocalPersonaColors.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ZenSpacing.sectionGap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonaSigil()
            Spacer(Modifier.width(ZenSpacing.lg))
            Column {
                Text(
                    text = LineLibrary.shieldTitle(persona),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary
                )
                Text(
                    text = LineLibrary.shieldDescription(persona),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            }
        }
    }
}

/** One card listing the missing grants — shown only while setup is incomplete. Nothing pulses. */
@Composable
private fun SetupCard(
    accessibilityGranted: Boolean,
    usageGranted: Boolean,
    onAccessibility: () -> Unit,
    onUsage: () -> Unit
) {
    val c = LocalPersonaColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(ZenSpacing.md))
            if (!accessibilityGranted) {
                SetupRow(
                    title = "Accessibility service",
                    description = "Required — this is what detects and intercepts short-form feeds.",
                    onClick = onAccessibility
                )
            }
            if (!accessibilityGranted && !usageGranted) Spacer(Modifier.height(ZenSpacing.md))
            if (!usageGranted) {
                SetupRow(
                    title = "Usage access",
                    description = "Optional — enables the screen-time stats on this dashboard.",
                    onClick = onUsage
                )
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, description: String, onClick: () -> Unit) {
    val c = LocalPersonaColors.current
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZenRadius.chip)
            .clickable(interactionSource = source, indication = null) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(c.warn)
        )
        Spacer(Modifier.width(ZenSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
fun AppStatsCard(app: AppUsageItem, capMinutes: Int) {
    val c = LocalPersonaColors.current
    val appColor = remember(app.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(app.colorHex))
        } catch (e: Exception) {
            c.accent
        }
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), shape = ZenRadius.chip, contentPadding = ZenSpacing.md) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(appColor)
                    )
                    Spacer(Modifier.width(ZenSpacing.sm))
                    Text(app.appName, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                }
                Text(
                    text = "${app.timeSpentMinutes} min",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = c.textPrimary
                )
            }
            Spacer(Modifier.height(ZenSpacing.sm))
            // Bars are normalized against the user's daily cap so length means something.
            val progress = remember(app.timeSpentMinutes, capMinutes) {
                (app.timeSpentMinutes / capMinutes.toFloat().coerceAtLeast(1f)).coerceIn(0.02f, 1f)
            }
            LinearProgressIndicator(
                progress = { progress },
                color = appColor,
                trackColor = c.textPrimary.copy(alpha = 0.05f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}
