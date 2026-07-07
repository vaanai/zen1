package com.example.zen.ui.guard

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.zen.ui.components.PrimaryButton
import com.example.zen.ui.components.SecondaryButton
import com.example.zen.ui.components.SectionHeader
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
        Spacer(Modifier.height(ZenSpacing.xl))
        Text("Guard", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(ZenSpacing.lg))

        if (!unlocked) {
            LockGate(prefs, tick)
        } else {
            UnlockedGuardSettings(prefs)
        }
        Spacer(Modifier.height(ZenSpacing.xxl))
    }
}

@Composable
private fun UnlockedGuardSettings(prefs: ZenPrefs) {
    val c = LocalPersonaColors.current

    // prefs.revision is not Compose state; a local counter invalidates the derived config map
    // after every write so the cards refresh.
    var localRev by remember { mutableStateOf(0) }
    val configs = remember(localRev) { prefs.appConfigs }
    var lenient by remember { mutableStateOf(prefs.earnedScrollsEnabled) }

    SectionHeader("What you block")
    KnownApps.apps.forEach { app ->
        AppGuardCard(
            app = app,
            config = configs[app.key] ?: AppGuardConfig(),
            exits = remember { exitRoutesFor(app) },
            onChange = { transform ->
                prefs.updateConfig(app.key, transform)
                localRev++
            }
        )
        Spacer(Modifier.height(ZenSpacing.md))
    }

    Spacer(Modifier.height(ZenSpacing.lg))
    SectionHeader("Grace")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
            "Lenient mode",
            "One extra scroll of grace everywhere before an intervention. Off = strict.",
            lenient
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
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
                )
            }

            if (config.enabled) {
                Spacer(Modifier.height(ZenSpacing.md))
                HairlineDivider()
                Spacer(Modifier.height(ZenSpacing.md))

                StepperRow(
                    label = if (config.scrollAllowance == 0) "Intervene the moment you open a feed"
                    else "Allow ${config.scrollAllowance} scroll(s) first",
                    value = config.scrollAllowance,
                    range = 0..5,
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
                    onSelect = { action -> onChange { it.copy(exitAction = action) } }
                )

                Spacer(Modifier.height(ZenSpacing.md))
                HairlineDivider()
                ToggleRow(
                    "Friend pass",
                    "Allow the one video a friend sends you in DMs.",
                    config.friendPass
                ) { on -> onChange { it.copy(friendPass = on) } }
            }
        }
    }
}

@Composable
private fun ExitPickerRow(
    selected: ExitAction,
    exits: ExitRoutes,
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
                onClick = { onSelect(action) }
            )
        }
    }
}

@Composable
private fun SelectablePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPersonaColors.current
    Box(
        modifier = Modifier
            .clip(ZenRadius.pill)
            .background(if (selected) c.accent.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = ZenSpacing.md, vertical = ZenSpacing.sm)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) c.accent else c.textSecondary
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    val c = LocalPersonaColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            modifier = Modifier.weight(1f)
        )
        StepperButton("−", enabled = value > range.first) { onValue((value - 1).coerceIn(range)) }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = c.textPrimary,
            modifier = Modifier.padding(horizontal = ZenSpacing.md)
        )
        StepperButton("+", enabled = value < range.last) { onValue((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun StepperButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalPersonaColors.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(c.textPrimary.copy(alpha = if (enabled) 0.08f else 0.03f))
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
private fun ToggleRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
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
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent)
        )
    }
}

/** 1dp low-alpha divider between rows inside a sectioned card. */
@Composable
private fun HairlineDivider() {
    val c = LocalPersonaColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.textPrimary.copy(alpha = 0.06f))
    )
}

@Composable
private fun PasswordManagement(prefs: ZenPrefs) {
    val c = LocalPersonaColors.current
    var newPassword by remember { mutableStateOf(prefs.lockPassword) }
    var showPassword by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = newPassword,
            onValueChange = { if (it.length <= ZenPrefs.PASSWORD_LENGTH) newPassword = it },
            label = { Text("Password (${ZenPrefs.PASSWORD_LENGTH} chars, blank = none)") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            supportingText = { Text("${newPassword.length} / ${ZenPrefs.PASSWORD_LENGTH}") },
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide" else "Show", color = c.accent, style = MaterialTheme.typography.labelSmall)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.accent, focusedLabelColor = c.accent, cursorColor = c.accent
            ),
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
            Icon(Icons.Default.Lock, null, tint = c.accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(ZenSpacing.md))
            Text("Settings are locked", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(ZenSpacing.sm))
            Text(
                "You committed to this on purpose. Changing it should take a moment of intention.",
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
                Text("Stay on this screen — it'll open automatically.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(ZenSpacing.md))
                TextButton(onClick = { prefs.cancelCooldown() }) { Text("Cancel", color = c.textSecondary) }
            } else {
                if (prefs.hasPassword()) {
                    OutlinedTextField(
                        value = attempt,
                        onValueChange = { attempt = it; error = false },
                        label = { Text("Enter password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = error,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.accent, focusedLabelColor = c.accent, cursorColor = c.accent
                        ),
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
                        "No password set — unlocking just takes a 2-minute wait.",
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
