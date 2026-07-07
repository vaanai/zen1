package com.example.zen.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zen.data.KnownApps
import com.example.zen.data.ZenPrefs
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.persona.Persona
import com.example.zen.ui.components.GlassCard
import com.example.zen.ui.components.LocalHazeState
import com.example.zen.ui.components.PrimaryButton
import com.example.zen.ui.components.SecondaryButton
import com.example.zen.ui.components.SectionHeader
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    prefs: ZenPrefs,
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit = {}
) {
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
    val hazeState = rememberHazeState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(c.gradient))
            .hazeSource(hazeState)
    ) {
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZenSpacing.md, vertical = ZenSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
                    }
                    Text("Settings", style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.sp), color = c.textPrimary)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ZenSpacing.screenGutter)
                ) {
                    if (!unlocked) LockGate(prefs, tick) else UnlockedSettings(prefs, selectedPersona, onPersonaSelected)
                    Spacer(Modifier.height(ZenSpacing.xl))
                    VersionFooter(onOpenDebug = onOpenDebug)
                    Spacer(Modifier.height(ZenSpacing.xxl))
                }
            }
        }
    }
}

@Composable
private fun LockGate(prefs: ZenPrefs, tick: Int) {
    val c = LocalPersonaColors.current
    var attempt by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val cooldownPending = remember(tick) { prefs.isCooldownPending() }
    val remaining = remember(tick) { prefs.cooldownRemainingMs() }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ZenSpacing.xl),
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
                Text("Unlocking in ${formatMs(remaining)}", style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.sp), color = c.accent)
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

@Composable
private fun UnlockedSettings(
    prefs: ZenPrefs,
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit
) {
    val c = LocalPersonaColors.current

    val selectedApps = remember {
        mutableStateListOf<String>().apply {
            addAll(KnownApps.apps.filter { app -> app.packages.any { it in prefs.blockedPackages } }.map { it.name })
        }
    }
    var friendPass by remember { mutableStateOf(prefs.friendPassEnabled) }
    var allowedScrolls by remember { mutableStateOf(prefs.allowedScrolls.toFloat()) }
    var dailyCap by remember { mutableStateOf(prefs.dailyCapMinutes.toFloat()) }
    var earned by remember { mutableStateOf(prefs.earnedScrollsEnabled) }
    var newPassword by remember { mutableStateOf(prefs.lockPassword) }
    var showPassword by remember { mutableStateOf(false) }

    fun writeApps() {
        prefs.blockedPackages = KnownApps.apps.filter { it.name in selectedApps }.flatMap { it.packages }.toSet()
    }

    Spacer(Modifier.height(ZenSpacing.sm))
    SectionHeader("Persona")
    Persona.entries.forEach { p ->
        val isSel = p == selectedPersona
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ZenSpacing.xs)
                .clickable { prefs.persona = p; onPersonaSelected(p) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(p.glyph, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp))
            Spacer(Modifier.width(ZenSpacing.md))
            Text(p.displayName, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (isSel) Icon(Icons.Default.CheckCircle, "Selected", tint = c.accent)
        }
    }

    Spacer(Modifier.height(ZenSpacing.xl))
    SectionHeader("Guarded apps")
    KnownApps.apps.forEach { app ->
        ToggleRow(app.name, null, app.name in selectedApps) { on ->
            if (on) selectedApps.add(app.name) else selectedApps.remove(app.name)
            writeApps()
        }
    }

    Spacer(Modifier.height(ZenSpacing.xl))
    SectionHeader("Blocking")
    ToggleRow(
        "Friend Pass",
        "Allow the one video a friend DM'd you; block the next scroll.",
        friendPass
    ) { friendPass = it; prefs.friendPassEnabled = it }
    Spacer(Modifier.height(ZenSpacing.md))
    Text(
        if (allowedScrolls.toInt() == 0) "Strictness: block the moment you open a feed"
        else "Strictness: allow ${allowedScrolls.toInt()} scroll(s) before blocking",
        style = MaterialTheme.typography.titleMedium, color = c.textPrimary
    )
    Slider(
        value = allowedScrolls,
        onValueChange = { allowedScrolls = it; prefs.allowedScrolls = it.toInt() },
        valueRange = 0f..5f,
        steps = 4,
        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent)
    )

    Spacer(Modifier.height(ZenSpacing.sm))
    SectionHeader("Goals")
    Text("Daily screen-time goal: ${dailyCap.toInt()} min", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
    Slider(
        value = dailyCap,
        onValueChange = { dailyCap = it; prefs.dailyCapMinutes = it.toInt() },
        valueRange = 15f..240f,
        steps = 14,
        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent)
    )
    ToggleRow(
        "Lenient mode",
        "Give yourself a little extra grace before a block kicks in, instead of an outright wall. Off = strict.",
        earned
    ) { earned = it; prefs.earnedScrollsEnabled = it }

    Spacer(Modifier.height(ZenSpacing.xl))
    SectionHeader("Commitment lock")
    OutlinedTextField(
        value = newPassword,
        onValueChange = { if (it.length <= ZenPrefs.PASSWORD_LENGTH) newPassword = it },
        label = { Text("Password (${ZenPrefs.PASSWORD_LENGTH} chars, blank = none)") },
        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
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

    Spacer(Modifier.height(ZenSpacing.xl))
    SecondaryButton("Lock settings now", onClick = { prefs.lockNow() }, modifier = Modifier.fillMaxWidth())
}

/**
 * Build identity footer — the anti-"did my sideload even change anything" measure.
 * Seven taps opens the hidden diagnostics screen.
 */
@Composable
private fun VersionFooter(onOpenDebug: () -> Unit) {
    val c = LocalPersonaColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { com.example.zen.data.AppVersion.describe(context) }
    var taps by remember { mutableStateOf(0) }

    Text(
        text = "Zen $version",
        style = MaterialTheme.typography.labelSmall,
        color = c.textSecondary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                taps++
                if (taps >= 7) {
                    taps = 0
                    onOpenDebug()
                }
            }
            .padding(vertical = ZenSpacing.sm)
    )
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

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(java.util.Locale.US, "%d:%02d", m, s)
}
