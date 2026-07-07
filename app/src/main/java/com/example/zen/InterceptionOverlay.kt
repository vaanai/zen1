package com.example.zen

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.zen.persona.Persona
import com.example.zen.persona.PersonaTheme
import com.example.zen.ui.intervention.InterventionScreen

/** Everything the friction screen needs to render one interception. */
data class InterventionRequest(
    val persona: Persona,
    /** Escalation tier (0..2) from today's relapse count; drives copy and override hold time. */
    val tier: Int,
    /** The persona's line — the one humor slot on the screen. */
    val line: String,
    /** Display name of the intercepted app ("Instagram"), used on the Leave button. */
    val appName: String,
    /** Which interception of the day this is (1-based), for the factual kicker. */
    val interceptionOrdinal: Int,
    /** Minutes spent in the app today, or null when Usage Access isn't granted. */
    val minutesInAppToday: Long?
)

/**
 * Callbacks the friction screen routes back to the service. Dismissal is NEVER fire-and-forget:
 * every way off the screen goes through the service's state machine, which owns navigation.
 */
interface InterventionHost {
    /** User chose to leave (Leave button, back key, or watchdog). Service navigates, then dismisses. */
    fun onLeaveRequested()

    /** User completed the hold-to-continue override. Service grants a timed pass, then dismisses. */
    fun onOverrideCompleted()
}

/**
 * Hosts the friction screen in a [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] window
 * owned by the Accessibility Service (no `SYSTEM_ALERT_WINDOW` needed).
 *
 * The window is focusable so it receives the back key — back means "leave", routed through
 * [InterventionHost.onLeaveRequested] like every other exit. There is no auto-dismiss: the
 * overlay stays up until the service confirms the escape (masking the navigation underneath)
 * or the user leaves via the home gesture (the service sees the foreground change and calls
 * [dismiss]).
 *
 * Because the overlay lives outside an Activity, the [ComposeView] gets its own minimal
 * lifecycle / saved-state / view-model owners.
 */
class InterceptionOverlay(
    private val service: AccessibilityService,
    private val host: InterventionHost
) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var lifecycleHost: OverlayLifecycleOwner? = null

    val isShowing: Boolean get() = current != null

    fun show(request: InterventionRequest) {
        handler.post {
            removeNow()

            val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
            val composeView = ComposeView(service).apply {
                setContent {
                    PersonaTheme(request.persona) {
                        InterventionScreen(
                            request = request,
                            onLeave = { host.onLeaveRequested() },
                            onOverrideCompleted = { host.onOverrideCompleted() }
                        )
                    }
                }
            }
            // ComposeView is final, so back-key handling lives on a wrapping FrameLayout.
            // The view-tree owners go on the wrapper: descendants look them up the parent chain.
            val frame = BackHandlingFrame(service) { host.onLeaveRequested() }.apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                addView(composeView)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Focusable on purpose: the window must receive KEYCODE_BACK.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(frame, params)
                lifecycleOwner.onResume()
                current = frame
                lifecycleHost = lifecycleOwner
            } catch (_: Exception) {
                lifecycleOwner.onDestroy()
                current = null
                lifecycleHost = null
            }
        }
    }

    /** Called by the service once the escape is confirmed, the foreground app changed, or on destroy. */
    fun dismiss() {
        handler.post { removeNow() }
    }

    private fun removeNow() {
        current?.let { v ->
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
            }
        }
        lifecycleHost?.onDestroy()
        current = null
        lifecycleHost = null
    }
}

/** A host view that maps the back key to "leave" instead of letting the system handle it. */
private class BackHandlingFrame(
    context: Context,
    private val onBack: () -> Unit
) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Minimal lifecycle / saved-state / view-model owner so a [ComposeView] can run outside an Activity. */
private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
