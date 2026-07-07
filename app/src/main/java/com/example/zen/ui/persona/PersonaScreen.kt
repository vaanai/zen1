package com.example.zen.ui.persona

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.zen.data.ZenPrefs
import com.example.zen.persona.LineLibrary
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.persona.Persona
import com.example.zen.persona.PersonaPalette
import com.example.zen.ui.components.GlassCard
import com.example.zen.ui.components.PersonaMark
import com.example.zen.ui.components.PersonaSigil
import com.example.zen.ui.components.SectionHeader
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing

/**
 * The Persona page — pick the app's voice and skin. Each card renders in its OWN palette
 * (choosing a persona is choosing a theme); selection reskins the whole app live.
 */
@Composable
fun PersonaScreen(
    prefs: ZenPrefs,
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalPersonaColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZenSpacing.screenGutter)
    ) {
        Spacer(Modifier.height(ZenSpacing.xl))
        Text("Persona", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Spacer(Modifier.height(ZenSpacing.sm))
        Text(
            "The voice that meets you on the way into a feed — and the whole app's skin.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary
        )
        Spacer(Modifier.height(ZenSpacing.lg))

        Persona.entries.forEach { p ->
            val pc = PersonaPalette.of(p)
            val isSel = p == selectedPersona
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
                    .clickable(interactionSource = source, indication = null) {
                        prefs.persona = p
                        onPersonaSelected(p)
                    }
                    .padding(ZenSpacing.cardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonaMark(persona = p, size = 28.dp, color = pc.accent)
                Spacer(Modifier.width(ZenSpacing.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium, color = pc.textPrimary)
                    Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = pc.textSecondary)
                }
                if (isSel) Icon(Icons.Default.CheckCircle, "Selected", tint = pc.accent)
            }
        }

        Spacer(Modifier.height(ZenSpacing.lg))
        SectionHeader("On duty")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonaSigil()
                Spacer(Modifier.width(ZenSpacing.lg))
                Column {
                    Text(
                        LineLibrary.shieldTitle(selectedPersona),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary
                    )
                    Text(
                        LineLibrary.shieldDescription(selectedPersona),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(ZenSpacing.lg))
        // The persona introduces itself — set as a quote.
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
                LineLibrary.welcome(selectedPersona),
                style = MaterialTheme.typography.bodyLarge,
                color = c.textPrimary,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
        Spacer(Modifier.height(ZenSpacing.xxl))
    }
}
