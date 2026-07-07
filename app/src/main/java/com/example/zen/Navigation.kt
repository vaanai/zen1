package com.example.zen

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.zen.data.DefaultZenStatusProvider
import com.example.zen.data.ZenPrefs
import com.example.zen.detection.DetectionConfigStore
import com.example.zen.persona.PersonaTheme
import com.example.zen.ui.debug.DebugScreen
import com.example.zen.ui.home.HomeScaffold
import com.example.zen.ui.main.MainScreenViewModel
import com.example.zen.ui.onboarding.OnboardingScreen
import com.example.zen.ui.settings.AboutScreen
import kotlinx.coroutines.delay

/**
 * Root of the app. Holds the active persona (so switching it reskins everything live), wraps the
 * persona theme, and hosts onboarding → dashboard → settings navigation.
 */
@Composable
fun ZenApp() {
    val context = LocalContext.current
    val prefs = remember { ZenPrefs(context.applicationContext) }
    val statusProvider = remember { DefaultZenStatusProvider(context.applicationContext) }

    var persona by remember { mutableStateOf(prefs.persona) }
    val setPersona: (com.example.zen.persona.Persona) -> Unit = { p -> persona = p; prefs.persona = p }

    // Live permission status for onboarding / dashboard.
    var accessibilityEnabled by remember { mutableStateOf(statusProvider.isAccessibilityEnabled()) }
    var usageEnabled by remember { mutableStateOf(statusProvider.isUsageAccessEnabled()) }
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = statusProvider.isAccessibilityEnabled()
            usageEnabled = statusProvider.isUsageAccessEnabled()
            delay(1500)
        }
    }

    // Opportunistic detection-config refresh on app open (throttled + silent inside the store).
    LaunchedEffect(Unit) {
        DetectionConfigStore.init(context.applicationContext)
        DetectionConfigStore.refreshFromRemote(context.applicationContext)
    }

    // Status/nav bar icon contrast follows the PERSONA, not the system dark-mode setting —
    // otherwise light personas (Zen/Sage) get invisible white status icons on parchment.
    val view = LocalView.current
    val personaIsLight = com.example.zen.persona.PersonaPalette.of(persona).isLight
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = personaIsLight
        controller.isAppearanceLightNavigationBars = personaIsLight
    }

    PersonaTheme(persona) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val backStack = rememberNavBackStack(if (prefs.onboardingComplete) Dashboard else Onboarding)

            // Edge-to-edge: backdrops paint under the system bars; each screen insets its own
            // content (a root safeDrawingPadding would put a visible seam at the status bar).
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize()
            ) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    transitionSpec = {
                        (fadeIn(tween(320)) + slideInHorizontally(tween(320)) { it / 10 }) togetherWith
                            fadeOut(tween(180))
                    },
                    popTransitionSpec = {
                        fadeIn(tween(280)) togetherWith
                            (fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { it / 10 })
                    },
                    entryProvider = entryProvider {
                        entry<Onboarding> {
                            OnboardingScreen(
                                prefs = prefs,
                                selectedPersona = persona,
                                onPersonaSelected = setPersona,
                                isAccessibilityEnabled = accessibilityEnabled,
                                isUsageEnabled = usageEnabled,
                                onFinish = {
                                    backStack.clear()
                                    backStack.add(Dashboard)
                                }
                            )
                        }
                        entry<Dashboard> {
                            val viewModel: MainScreenViewModel =
                                viewModel { MainScreenViewModel(statusProvider, prefs) }
                            HomeScaffold(
                                viewModel = viewModel,
                                prefs = prefs,
                                selectedPersona = persona,
                                onPersonaSelected = setPersona,
                                onOpenAbout = { backStack.add(SettingsRoute) }
                            )
                        }
                        entry<SettingsRoute> {
                            AboutScreen(
                                isAccessibilityEnabled = accessibilityEnabled,
                                isUsageEnabled = usageEnabled,
                                onBack = { backStack.removeLastOrNull() },
                                onOpenDebug = { backStack.add(DebugRoute) }
                            )
                        }
                        entry<DebugRoute> {
                            DebugScreen(
                                prefs = prefs,
                                onBack = { backStack.removeLastOrNull() }
                            )
                        }
                    }
                )
            }
        }
    }
}
