package com.example.zen.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zen.data.ZenPrefs
import com.example.zen.persona.LocalPersonaColors
import com.example.zen.persona.Persona
import com.example.zen.ui.components.PersonaBackdrop
import com.example.zen.ui.design.ZenRadius
import com.example.zen.ui.design.ZenSpacing
import com.example.zen.ui.guard.GuardScreen
import com.example.zen.ui.main.MainScreen
import com.example.zen.ui.main.MainScreenViewModel
import com.example.zen.ui.persona.PersonaScreen

/**
 * The 3-page main app: Today (wellbeing), Guard (what you block), Persona (theme), under a
 * quiet persona-themed tab bar. Housekeeping (permissions/privacy/version) lives on the
 * separate Settings/About screen reached from Today's gear icon.
 */
@Composable
fun HomeScaffold(
    viewModel: MainScreenViewModel,
    prefs: ZenPrefs,
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit,
    onOpenAbout: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()

    PersonaBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding()
            ) {
                // Crossfade between peers; SaveableStateProvider keeps each tab's scroll state.
                Crossfade(targetState = tab, animationSpec = tween(200), label = "tabSwitch") { t ->
                    stateHolder.SaveableStateProvider(t) {
                        when (t) {
                            0 -> MainScreen(viewModel = viewModel, prefs = prefs, onOpenAbout = onOpenAbout)
                            1 -> GuardScreen(prefs = prefs)
                            else -> PersonaScreen(
                                prefs = prefs,
                                selectedPersona = selectedPersona,
                                onPersonaSelected = onPersonaSelected
                            )
                        }
                    }
                }
            }
            ZenTabBar(selected = tab, onSelect = { tab = it })
        }
    }
}

private data class TabSpec(val label: String, val icon: ImageVector)

@Composable
private fun ZenTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalPersonaColors.current
    val tabs = listOf(
        TabSpec("Today", Icons.Outlined.DonutLarge),
        TabSpec("Guard", Icons.Outlined.Shield),
        TabSpec("Persona", Icons.Outlined.Palette)
    )

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.hairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.gradient.first())
                .navigationBarsPadding()
                .padding(vertical = ZenSpacing.sm)
        ) {
            tabs.forEachIndexed { index, tabSpec ->
                val isSelected = index == selected
                val tint by animateColorAsState(
                    targetValue = if (isSelected) c.accent else c.textSecondary,
                    animationSpec = tween(200),
                    label = "tabTint"
                )
                val pillFill by animateColorAsState(
                    targetValue = if (isSelected) c.accent.copy(alpha = 0.12f)
                    else c.accent.copy(alpha = 0f),
                    animationSpec = tween(200),
                    label = "tabPill"
                )
                val source = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(interactionSource = source, indication = null) { onSelect(index) }
                        .padding(vertical = ZenSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ZenRadius.pill)
                            .background(pillFill)
                            .padding(horizontal = ZenSpacing.lg, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = tabSpec.icon,
                            contentDescription = tabSpec.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(ZenSpacing.xs))
                    Text(
                        text = tabSpec.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = tint
                    )
                }
            }
        }
    }
}
