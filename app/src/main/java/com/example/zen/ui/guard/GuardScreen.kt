package com.example.zen.ui.guard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.zen.data.AppGuardConfig
import com.example.zen.data.ExitAction
import com.example.zen.data.GuardedApp
import com.example.zen.data.KnownApps
import com.example.zen.data.ZenPrefs
import com.example.zen.detection.DetectionConfigStore
import com.example.zen.detection.ExitRoutes
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.components.GlassCard
import com.example.zen.ui.components.PageHeader
import com.example.zen.ui.components.PrimaryButton
import com.example.zen.ui.components.SecondaryButton
import com.example.zen.ui.components.SectionHeader
import com.example.zen.ui.components.ZenSwitch
import com.example.zen.ui.components.ZenTextField
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing
import kotlinx.coroutines.delay

/**
 * The Guard page — everything about what gets blocked and how, one card per guarded app:
 * enable, scroll allowance, exit destination, friend pass. All edits sit behind the
 * commitment [LockGate]; the customization is always *reachable*, never *casual*.
 */
@Composable
fun GuardScreen(prefs: ZenPrefs, modifier: Modifier = Modifier) {
    val c = LocalPersonaColors.current

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            prefs.completeCooldownIfReady()
            tick++
            delay(1000)
        }
    }
    val unlocked = remember(tick) { prefs.isUnlocked() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZenSpacing.screenGutter)
    ) {
        PageHeader(
            title = "Guard",
            subtitle = if (unlocked) "UNLOCKED — CHANGES ALLOWED" else "LOCKED",
            subtitleColor = if (unlocked) c.accent else null
        )

        if (!unlocked) {
            LockGate(prefs, tick)
            Spacer(Modifier.height(ZenSpacing.lg))
            // The commitments stay visible while locked — read-only, not hidden. An empty
            // locked page reads as a broken page.
            GuardCards(prefs, editable = false)
        } else {
            GuardCards(prefs, editable = true)
            UnlockedExtras(prefs)
        }
        Spacer(Modifier.height(ZenSpacing.xxl))
    }
}

@Composable
private fun GuardCards(prefs: ZenPrefs, editable: Boolean) {
    // prefs.revision is not Compose state; a local counter invalidates the derived config map
    // after every write so the cards refresh.
    var localRev by remember { mutableStateOf(0) }
    val configs = remember(localRev) { prefs.appConfigs }

    SectionHeader("What you block")
    KnownApps.apps.forEach { app ->
        Box(modifier = Modifier.alpha(if (editable) 1f else 0.6f)) {
            AppGuardCard(
                app = app,
                config = configs[app.key] ?: AppGuardConfig(),
                exits = remember { exitRoutesFor(app) },
                editable = editable,
                onChange = { transform ->
                    prefs.updateConfig(app.key, transform)
                    localRev++
                }
            )
        }
        Spacer(Modifier.height(ZenSpacing.md))
    }
}

@Composable
private fun UnlockedExtras(prefs: ZenPrefs) {
    var lenient by remember { mutableStateOf(prefs.earnedScrollsEnabled) }

    Spacer(Modifier.height(ZenSpacing.lg))
    SectionHeader("Grace")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
            "Lenient mode",
            "One extra scroll of grace before an intervention. Off is strict — which is rather the point.",
            lenient,
            enabled = true
        ) { lenient = it; prefs.earnedScrollsEnabled = it }
    }

    Spacer(Modifier.height(ZenSpacing.xl))
    SectionHeader("Commitment lock")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        PasswordManagement(prefs)
    }

    Spacer(Modifier.height(ZenSpacing.xl))
    SecondaryButton("Lock settings now", onClick = { prefs.lockNow() }, modifier = Modifier.fillMaxWidth())
}

/** Which exits the detection config says this app supports. */
private fun exitRoutesFor(app: GuardedApp): ExitRoutes =
    if (DetectionConfigStore.isInitialized) {
        DetectionConfigStore.current.matcherFor(app.packages.first())?.exits ?: ExitRoutes()
    } else {
        ExitRoutes()
    }

@Composable
private fun AppGuardCard(
    app: GuardedApp,
    config: AppGuardConfig,
    exits: ExitRoutes,
    editable: Boolean,
    onChange: ((AppGuardConfig) -> AppGuardConfig) -> Unit
) {
    val c = LocalPersonaColors.current
    val appColor = remember(app.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(app.colorHex))
        } catch (_: Exception) {
            c.accent
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(appColor)
                )
                Spacer(Modifier.width(ZenSpacing.md))
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                ZenSwitch(
                    checked = config.enabled,
                    enabled = editable,
                    onCheckedChange = { on -> onChange { it.copy(enabled = on) } }
                )
            }

            if (config.enabled) {
                Spacer(Modifier.height(ZenSpacing.md))
                HairlineDivider()
                Spacer(Modifier.height(ZenSpacing.md))

                StepperRow(
                    label = "Scrolls before stepping in",
                    caption = if (config.scrollAllowance == 0) "0 means the moment a feed opens." else null,
                    value = config.scrollAllowance,
                    range = 0..5,
                    enabled = editable,
                    onValue = { v -> onChange { it.copy(scrollAllowance = v) } }
                )

                Spacer(Modifier.height(ZenSpacing.md))
                HairlineDivider()
                Spacer(Modifier.height(ZenSpacing.md))

                Text("When you leave", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(ZenSpacing.sm))
                ExitPickerRow(
                    selected = config.exitAction,
                    exits = exits,
                    enabled = editable,
                    onSelect = { action -> onChange { it.copy(exitAction = action) } }
                )

                Spacer(Modifier.height(ZenSpacing.md))
                HairlineDivider()
                ToggleRow(
                    "Friend pass",
                    "The one video a friend sends you still gets through. The second one counts as scrolling.",
                    config.friendPass,
                    enabled = editable
                ) { on -> onChange { it.copy(friendPass = on) } }
            }
        }
    }
}

@Composable
private fun ExitPickerRow(
    selected: ExitAction,
    exits: ExitRoutes,
    enabled: Boolean,
    onSelect: (ExitAction) -> Unit
) {
    val options = buildList {
        add(ExitAction.LEAVE_APP to "Leave app")
        if (exits.appHome) add(ExitAction.APP_HOME to "App home")
        if (!exits.messagesUri.isNullOrBlank()) add(ExitAction.MESSAGES to "Messages")
    }
    // A stored action the config no longer supports displays (and behaves) as Leave app.
    val effective = if (options.any { it.first == selected }) selected else ExitAction.LEAVE_APP

    Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.sm)) {
        options.forEach { (action, label) ->
            SelectablePill(
                text = label,
                selected = action == effective,
                enabled = enabled,
                onClick = { onSelect(action) }
            )
        }
    }
}

@Composable
private fun SelectablePill(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalPersonaColors.current
    val fill by animateColorAsState(
        targetValue = if (selected) c.accent.copy(alpha = 0.18f) else c.surfaceDim,
        animationSpec = tween(150),
        label = "pillFill"
    )
    val content by animateColorAsState(
        targetValue = if (selected) c.accent else c.textSecondary,
        animationSpec = tween(150),
        label = "pillContent"
    )
    Box(
        modifier = Modifier
            .clip(ZenRadius.pill)
            .background(fill)
            .border(1.dp, if (selected) c.accent else c.cardBorder, ZenRadius.pill)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = ZenSpacing.md, vertical = ZenSpacing.sm)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun StepperRow(
    label: String,
    caption: String?,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValue: (Int) -> Unit
) {
    val c = LocalPersonaColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            StepperButton("−", enabled = enabled && value > range.first) { onValue((value - 1).coerceIn(range)) }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = ZenSpacing.md)
            )
            StepperButton("+", enabled = enabled && value < range.last) { onValue((value + 1).coerceIn(range)) }
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun StepperButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalPersonaColors.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) c.surfaceDim else c.surfaceDim.copy(alpha = c.surfaceDim.alpha * 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) c.textPrimary else c.textSecondary.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    val c = LocalPersonaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZenSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        ZenSwitch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/** 1dp divider between rows inside a sectioned card. */
@Composable
private fun HairlineDivider() {
    val c = LocalPersonaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.hairline)
    )
}

@Composable
private fun PasswordManagement(prefs: ZenPrefs) {
    val c = LocalPersonaColors.current
    var newPassword by remember { mutableStateOf(prefs.lockPassword) }
    var showPassword by remember { mutableStateOf(false) }

    Column {
        ZenTextField(
            value = newPassword,
            onValueChange = { if (it.length <= ZenPrefs.PASSWORD_LENGTH) newPassword = it },
            label = "Password (exactly ${ZenPrefs.PASSWORD_LENGTH} characters — blank for none)",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            supportingText = "${newPassword.length} / ${ZenPrefs.PASSWORD_LENGTH}",
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide" else "Show", color = c.accent, style = MaterialTheme.typography.labelSmall)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        PrimaryButton(
            text = "Save password",
            onClick = {
                if (newPassword.isEmpty() || newPassword.length == ZenPrefs.PASSWORD_LENGTH) {
                    prefs.lockPassword = newPassword
                }
            },
            enabled = newPassword.isEmpty() || newPassword.length == ZenPrefs.PASSWORD_LENGTH,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ZenSpacing.sm)
        )
    }
}

/** The commitment gate: password entry or the 2-minute cooldown. */
@Composable
fun LockGate(prefs: ZenPrefs, tick: Int) {
    val c = LocalPersonaColors.current
    var attempt by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val cooldownPending = remember(tick) { prefs.isCooldownPending() }
    val remaining = remember(tick) { prefs.cooldownRemainingMs() }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ZenSpacing.sm),
        shape = ZenRadius.hero,
        contentPadding = ZenSpacing.xl
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lock, null, tint = c.accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(ZenSpacing.md))
            Text("Settings are locked", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(ZenSpacing.sm))
            Text(
                "You locked these on purpose. Present-you doesn't get to overrule that on a whim.",
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(ZenSpacing.xl))

            if (cooldownPending) {
                Text(
                    "Unlocking in ${formatMs(remaining)}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    color = c.accent
                )
                Spacer(Modifier.height(ZenSpacing.xs))
                Text("Stay on this screen. Consider it a very short meditation.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(ZenSpacing.md))
                TextButton(onClick = { prefs.cancelCooldown() }) { Text("Cancel", color = c.textSecondary) }
            } else {
                if (prefs.hasPassword()) {
                    ZenTextField(
                        value = attempt,
                        onValueChange = { attempt = it; error = false },
                        label = "Enter password",
                        visualTransformation = PasswordVisualTransformation(),
                        isError = error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error) Text("Wrong password.", style = MaterialTheme.typography.bodySmall, color = c.danger)
                    Spacer(Modifier.height(ZenSpacing.md))
                    PrimaryButton("Unlock", onClick = { if (!prefs.tryPassword(attempt)) error = true }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(ZenSpacing.sm))
                    TextButton(onClick = { prefs.beginCooldown() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Forgot password? Unlock after 2 minutes", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text(
                        "No password set. Unlocking costs two minutes of waiting — the app's entire currency.",
                        style = MaterialTheme.typography.bodyMedium, color = c.textSecondary
                    )
                    Spacer(Modifier.height(ZenSpacing.md))
                    PrimaryButton("Start 2-minute unlock", onClick = { prefs.beginCooldown() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(java.util.Locale.US, "%d:%02d", m, s)
}
