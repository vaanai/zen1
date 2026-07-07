package com.example.zen.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.zen.data.AppVersion
import com.example.zen.data.ZenPrefs
import com.example.zen.detection.DetectionConfigStore
import com.example.zen.detection.DetectionLog
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.components.GlassCard
import com.example.zen.ui.components.PrimaryButton
import com.example.zen.ui.components.SecondaryButton
import com.example.zen.ui.components.SectionHeader
import com.example.zen.ui.design.ZenSpacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hidden diagnostics screen (7 taps on the Settings version footer). Shows the live detection
 * log, the active detection-config version, and controls for the verbose tree dump and manual
 * config refresh. This is the on-device half of the adb verification workflow.
 */
@Composable
fun DebugScreen(prefs: ZenPrefs, onBack: () -> Unit) {
    val c = LocalPersonaColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by DetectionLog.entries.collectAsState()

    var diagnostics by remember { mutableStateOf(prefs.diagnosticsEnabled) }
    var refreshStatus by remember { mutableStateOf<String?>(null) }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(c.gradient))
            .padding(ZenSpacing.screenGutter)
    ) {
        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.sp),
            color = c.textPrimary
        )
        Text(
            text = AppVersion.describe(context),
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary
        )

        Spacer(Modifier.height(ZenSpacing.lg))

        GlassCard {
            Column {
                val config = if (DetectionConfigStore.isInitialized) DetectionConfigStore.current else null
                Text(
                    text = "Detection config v${config?.configVersion ?: "?"} (${config?.updatedAt ?: "not loaded"})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary
                )
                refreshStatus?.let {
                    Spacer(Modifier.height(ZenSpacing.xs))
                    Text(text = it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
                Spacer(Modifier.height(ZenSpacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Verbose tree dump (ZenScan)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = diagnostics,
                        onCheckedChange = {
                            diagnostics = it
                            prefs.diagnosticsEnabled = it
                        }
                    )
                }
                Spacer(Modifier.height(ZenSpacing.md))
                PrimaryButton(
                    text = "Refresh config now",
                    onClick = {
                        refreshStatus = "Refreshing…"
                        scope.launch {
                            val result = DetectionConfigStore.refreshFromRemote(context, force = true)
                            refreshStatus = result.toString()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(ZenSpacing.lg))

        SectionHeader(text = "Detection log (${entries.size})")

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ZenSpacing.xs),
            reverseLayout = true // newest at the bottom, scrolled into view
        ) {
            items(entries) { entry ->
                Text(
                    text = buildString {
                        append(timeFormat.format(Date(entry.at)))
                        append("  ")
                        append(entry.event)
                        append("  ")
                        append(entry.pkg)
                        entry.matchedId?.let { append("  id=$it") }
                        entry.coverage?.let { append("  cov=%.2f".format(it)) }
                        if (entry.state.isNotEmpty()) append("  [${entry.state}]")
                        if (entry.decision.isNotEmpty()) append("  → ${entry.decision}")
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = c.textSecondary
                )
            }
        }

        Spacer(Modifier.height(ZenSpacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.md)) {
            SecondaryButton(text = "Clear log", onClick = { DetectionLog.clear() }, modifier = Modifier.weight(1f))
            SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
        }
    }
}
