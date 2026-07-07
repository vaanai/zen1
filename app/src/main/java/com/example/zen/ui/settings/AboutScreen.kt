package com.example.zen.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zen.data.AppVersion
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.components.GlassCard
import com.example.zen.ui.components.PersonaBackdrop
import com.example.zen.ui.components.SectionHeader
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing

/**
 * Housekeeping lives here so the three main pages stay focused: permission status + shortcuts,
 * the privacy/disclosure copy, and the build identity (7 taps opens the hidden diagnostics).
 */
@Composable
fun AboutScreen(
    isAccessibilityEnabled: Boolean,
    isUsageEnabled: Boolean,
    onBack: () -> Unit,
    onOpenDebug: () -> Unit
) {
    val c = LocalPersonaColors.current
    val context = LocalContext.current

    PersonaBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Back sits above the title, gutter-aligned with the content below.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZenSpacing.sm, vertical = ZenSpacing.xs)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.textPrimary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZenSpacing.screenGutter)
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                Spacer(Modifier.height(ZenSpacing.lg))
                SectionHeader("Permissions")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        PermissionRow(
                            title = "Accessibility service",
                            description = "Detects short-form feeds so Zen can step in. Required.",
                            granted = isAccessibilityEnabled,
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        )
                        Spacer(Modifier.height(ZenSpacing.md))
                        PermissionRow(
                            title = "Usage access",
                            description = "Powers the screen-time stats on Today. Optional, but the charts miss you.",
                            granted = isUsageEnabled,
                            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        )
                    }
                }

                Spacer(Modifier.height(ZenSpacing.xl))
                SectionHeader("Privacy")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Everything stays on this device. Zen has no servers, no accounts, and no analytics. Frankly, it can't afford them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary
                        )
                        Spacer(Modifier.height(ZenSpacing.md))
                        Text(
                            "The accessibility service is used for exactly one thing: recognizing when a " +
                                "short-form feed (Reels, Shorts, Spotlight, TikTok) is on screen, so it can be " +
                                "intercepted. Zen does not read, store, or transmit the content of your screen, " +
                                "messages, or anything else. Its only network use is checking this project's " +
                                "public repository for updated feed-detection rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(ZenSpacing.xl))
                VersionFooter(onOpenDebug = onOpenDebug)
                Spacer(Modifier.height(ZenSpacing.xxl))
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    val c = LocalPersonaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZenRadius.chip)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) c.safe else c.warn)
        )
        Spacer(Modifier.width(ZenSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(description, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(ZenSpacing.md))
        Text(
            text = if (granted) "On" else "Grant",
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) c.safe else c.accent
        )
    }
}

/** Build identity — seven taps opens the hidden diagnostics screen. */
@Composable
private fun VersionFooter(onOpenDebug: () -> Unit) {
    val c = LocalPersonaColors.current
    val context = LocalContext.current
    val version = remember { AppVersion.describe(context) }
    var taps by remember { mutableStateOf(0) }

    Text(
        text = "Zen $version",
        style = MaterialTheme.typography.labelSmall,
        color = c.textSecondary,
        textAlign = TextAlign.Center,
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
