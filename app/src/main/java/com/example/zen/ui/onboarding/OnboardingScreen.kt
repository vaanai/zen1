package com.example.zen.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zen.data.KnownApps
import com.example.zen.data.ZenPrefs
import com.example.zen.persona.LineLibrary
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.persona.Persona
import com.example.zen.persona.PersonaPalette
import com.example.zen.ui.components.GlassCard
import com.example.zen.ui.components.PersonaBackdrop
import com.example.zen.ui.components.PersonaMark
import com.example.zen.ui.components.PrimaryButton
import com.example.zen.ui.components.SecondaryButton
import com.example.zen.ui.components.ZenSlider
import com.example.zen.ui.components.ZenSwitch
import com.example.zen.ui.components.ZenTextField
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing

@Composable
fun OnboardingScreen(
    prefs: ZenPrefs,
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit,
    isAccessibilityEnabled: Boolean,
    isUsageEnabled: Boolean,
    onFinish: () -> Unit
) {
    val c = LocalPersonaColors.current
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }

    val selectedApps = remember {
        mutableStateListOf<String>().apply {
            addAll(KnownApps.apps.filter { app -> app.packages.any { it in prefs.blockedPackages } }.map { it.name })
        }
    }
    var friendPass by rememberSaveable { mutableStateOf(prefs.friendPassEnabled) }
    var dailyCap by rememberSaveable { mutableStateOf(prefs.dailyCapMinutes.toFloat()) }
    var password by rememberSaveable { mutableStateOf("") }

    val totalSteps = 4

    PersonaBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(ZenSpacing.screenGutter)
        ) {
                StepSegments(current = step, total = totalSteps)
                Spacer(Modifier.height(ZenSpacing.xl))

                // Directional step transition: forward slides left, Back slides right.
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val enter = fadeIn(tween(220)) + slideInHorizontally(tween(220)) {
                            if (forward) it / 8 else -it / 8
                        }
                        val exit = fadeOut(tween(160))
                        enter togetherWith exit
                    },
                    modifier = Modifier.weight(1f),
                    label = "onboardingStep"
                ) { s ->
                    when (s) {
                        0 -> StepPersona(selectedPersona, onPersonaSelected)
                        1 -> StepPermissions(
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            isUsageEnabled = isUsageEnabled,
                            onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            onOpenUsage = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        )
                        2 -> StepConfig(
                            selectedApps = selectedApps,
                            friendPass = friendPass,
                            onFriendPassChange = { friendPass = it },
                            dailyCap = dailyCap,
                            onDailyCapChange = { dailyCap = it }
                        )
                        else -> StepLock(password = password, onPasswordChange = { password = it })
                    }
                }

                Spacer(Modifier.height(ZenSpacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZenSpacing.md)
                ) {
                    if (step > 0) {
                        SecondaryButton("Back", onClick = { step-- }, modifier = Modifier.weight(1f))
                    }
                    PrimaryButton(
                        text = if (step < totalSteps - 1) "Continue" else "Begin",
                        onClick = {
                            if (step < totalSteps - 1) {
                                step++
                            } else {
                                commit(prefs, selectedApps, friendPass, dailyCap.toInt(), password)
                                onFinish()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
        }
    }
}

private fun commit(
    prefs: ZenPrefs,
    selectedAppNames: List<String>,
    friendPass: Boolean,
    dailyCap: Int,
    password: String
) {
    val packages = KnownApps.apps
        .filter { it.name in selectedAppNames }
        .flatMap { it.packages }
        .toSet()
    prefs.blockedPackages = packages
    prefs.friendPassEnabled = friendPass // legacy seed for migration
    KnownApps.apps.forEach { app ->
        prefs.updateConfig(app.key) { it.copy(friendPass = friendPass) }
    }
    prefs.dailyCapMinutes = dailyCap
    if (password.length == ZenPrefs.PASSWORD_LENGTH) {
        prefs.lockPassword = password
    }
    prefs.onboardingComplete = true
    prefs.lockNow()
}

/** Quiet progress: four 2dp segments, filled up to the current step. */
@Composable
private fun StepSegments(current: Int, total: Int) {
    val c = LocalPersonaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZenSpacing.sm)
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .clip(ZenRadius.pill)
                    .background(if (i <= current) c.accent else c.textPrimary.copy(alpha = 0.08f))
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String? = null) {
    val c = LocalPersonaColors.current
    Text(title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
    if (subtitle != null) {
        Spacer(Modifier.height(ZenSpacing.sm))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
    Spacer(Modifier.height(ZenSpacing.lg))
}

@Composable
private fun StepPersona(selected: Persona, onSelect: (Persona) -> Unit) {
    val c = LocalPersonaColors.current
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        StepTitle(
            "Choose your guardian",
            "Colors, tone, and how you'll be talked out of a feed. Change it anytime — they won't take it personally."
        )
        // Each card is rendered in its OWN palette: choosing a persona is choosing a theme,
        // so the picker shows the design system instead of describing it.
        Persona.entries.forEach { p ->
            val pc = PersonaPalette.of(p)
            val isSel = p == selected
            val source = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ZenSpacing.md)
                    .clip(ZenRadius.card)
                    .background(pc.gradient[1])
                    .border(
                        width = if (isSel) 1.5.dp else 1.dp,
                        color = if (isSel) c.accent else pc.cardBorder,
                        shape = ZenRadius.card
                    )
                    .clickable(interactionSource = source, indication = null) { onSelect(p) }
                    .padding(ZenSpacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonaMark(persona = p, size = 28.dp, color = pc.accent)
                Spacer(Modifier.width(ZenSpacing.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium, color = pc.textPrimary)
                    Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = pc.textSecondary)
                }
                if (isSel) Icon(Icons.Outlined.CheckCircle, "Selected", tint = pc.accent)
            }
        }
        Spacer(Modifier.height(ZenSpacing.md))
        // The selected persona introduces itself — set as a quote, not a toast.
        Row {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .clip(ZenRadius.pill)
                    .background(c.accent)
            )
            Spacer(Modifier.width(ZenSpacing.md))
            Text(
                LineLibrary.welcome(selected),
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun StepPermissions(
    isAccessibilityEnabled: Boolean,
    isUsageEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenUsage: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        StepTitle(
            "Grant access",
            "One permission does the work; the other draws the charts. Neither sends anything anywhere."
        )
        PermissionRow(
            title = "Accessibility Service",
            desc = "Lets Zen see when you're scrolling a short-form feed so it can step in. Zen does not read your messages or collect content.",
            granted = isAccessibilityEnabled,
            onClick = onOpenAccessibility
        )
        Spacer(Modifier.height(ZenSpacing.md))
        PermissionRow(
            title = "Usage Access",
            desc = "Optional. The dashboard is nicer with it, and dashboards are how this app flatters you.",
            granted = isUsageEnabled,
            onClick = onOpenUsage
        )
        Spacer(Modifier.height(ZenSpacing.lg))
        Text(
            "Everything stays on this device. Zen has no servers to send it to.",
            style = MaterialTheme.typography.labelSmall,
            color = LocalPersonaColors.current.textSecondary
        )
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    val c = LocalPersonaColors.current
    val source = remember { MutableInteractionSource() }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZenRadius.card)
            .then(if (granted) Modifier.border(1.dp, c.safe, ZenRadius.card) else Modifier)
            .clickable(interactionSource = source, indication = null) { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(ZenSpacing.xs))
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            Spacer(Modifier.width(ZenSpacing.md))
            if (granted) {
                Icon(Icons.Outlined.CheckCircle, "Granted", tint = c.safe)
            } else {
                Text("Grant", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
        }
    }
}

@Composable
private fun StepConfig(
    selectedApps: MutableList<String>,
    friendPass: Boolean,
    onFriendPassChange: (Boolean) -> Unit,
    dailyCap: Float,
    onDailyCapChange: (Float) -> Unit
) {
    val c = LocalPersonaColors.current
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        StepTitle("What should I guard?")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                KnownApps.apps.forEach { app ->
                    val checked = app.name in selectedApps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ZenSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(app.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                        ZenSwitch(
                            checked = checked,
                            onCheckedChange = { on -> if (on) selectedApps.add(app.name) else selectedApps.remove(app.name) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(ZenSpacing.lg))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Friend Pass", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        "The one video a friend sends you still gets through. The second one counts as scrolling.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                    )
                }
                ZenSwitch(checked = friendPass, onCheckedChange = onFriendPassChange)
            }
        }
        Spacer(Modifier.height(ZenSpacing.xl))
        Text("Daily screen-time goal: ${dailyCap.toInt()} min", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        ZenSlider(
            value = dailyCap,
            onValueChange = onDailyCapChange,
            valueRange = 15f..240f,
            snap = 15
        )
    }
}

@Composable
private fun StepLock(password: String, onPasswordChange: (String) -> Unit) {
    val c = LocalPersonaColors.current
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        StepTitle(
            "Make it cost something",
            "A ${ZenPrefs.PASSWORD_LENGTH}-character password guards these settings from your future, weaker self. " +
                "Skipped it or forgot it? They still open — after a two-minute wait."
        )
        ZenTextField(
            value = password,
            onValueChange = { if (it.length <= ZenPrefs.PASSWORD_LENGTH) onPasswordChange(it) },
            label = "Password (optional)",
            visualTransformation = PasswordVisualTransformation(),
            supportingText = "${password.length} / ${ZenPrefs.PASSWORD_LENGTH}",
            modifier = Modifier.fillMaxWidth()
        )
        if (password.isNotEmpty() && password.length != ZenPrefs.PASSWORD_LENGTH) {
            Text(
                "Use exactly ${ZenPrefs.PASSWORD_LENGTH} characters, or leave blank to skip.",
                style = MaterialTheme.typography.bodySmall, color = c.warn
            )
        }
    }
}
