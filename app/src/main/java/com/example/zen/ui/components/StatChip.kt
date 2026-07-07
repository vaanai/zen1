package com.example.zen.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing

/**
 * A compact metric tile: big value over a tracked caption. Values render with tabular numerals
 * so a row of chips stays optically aligned as numbers change.
 * Built on [GlassCard] so it shares the frosted surface + border of every other card.
 */
@Composable
fun StatChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val c = LocalPersonaColors.current
    GlassCard(modifier = modifier, shape = ZenRadius.chip, contentPadding = ZenSpacing.md) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = c.textPrimary
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = ZenSpacing.xs)
            )
        }
    }
}
