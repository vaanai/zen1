package com.example.zen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.zen.data.ExitAction
import com.example.zen.data.KnownApps
import com.example.zen.data.ZenPrefs
import com.example.zen.detection.Detection
import com.example.zen.detection.DetectionConfigStore
import com.example.zen.detection.DetectionEngine
import com.example.zen.detection.DetectionLog
import com.example.zen.detection.ExitRoutes
import com.example.zen.detection.SessionStateMachine
import com.example.zen.detection.SessionStateMachine.Action
import com.example.zen.persona.LineLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Thin event router. All detection intelligence lives in [DetectionEngine] (what is on screen?)
 * and [SessionStateMachine] (what should happen?); this class only wires accessibility events,
 * user choices from the overlay, and scheduled re-checks into the machine, and executes the
 * actions it returns.
 */
class ZenAccessibilityService : AccessibilityService(), InterventionHost {

    private val TAG = "ZenBlocker"

    private lateinit var prefs: ZenPrefs
    private lateinit var engine: DetectionEngine
    private lateinit var machine: SessionStateMachine
    private var overlay: InterceptionOverlay? = null
    private var usageTracker: UsageTracker? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messagingPackages = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.facebook.orca",
        "com.discord",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    /** Packages that emit events without representing a real foreground app switch. */
    private val ignoredPackages = setOf("com.android.systemui")

    private var lastDirectMessageTime = 0L
    private var lastDumpTime = 0L
    private var lastPositiveLogAt = 0L
    private var lastLoggedState = ""

    private val exitCheckRunnable = Runnable { runExitCheck() }
    private val watchdogRunnable = Runnable {
        execute(machine.onWatchdog(System.currentTimeMillis()))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = ZenPrefs(applicationContext)
        DetectionConfigStore.init(applicationContext)
        engine = DetectionEngine { DetectionConfigStore.current }
        machine = SessionStateMachine(
            allowanceFor = { pkg ->
                val base = prefs.configFor(pkg).scrollAllowance
                if (prefs.earnedScrollsEnabled) base + 1 else base
            },
            friendPassActive = { pkg ->
                prefs.configFor(pkg).friendPass &&
                    System.currentTimeMillis() - lastDirectMessageTime < FRIEND_PASS_WINDOW_MS
            }
        )
        overlay = InterceptionOverlay(this, host = this)
        usageTracker = UsageTracker(applicationContext)

        // Refresh detection matchers if the cached config has gone stale (silent on failure).
        if (System.currentTimeMillis() - prefs.detectionConfigFetchedAt > DetectionConfigStore.STALE_AFTER_MS) {
            scope.launch { DetectionConfigStore.refreshFromRemote(applicationContext) }
        }

        Log.d(TAG, "Zen service connected. Guarding: ${prefs.blockedPackages}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in ignoredPackages) return
        val now = System.currentTimeMillis()

        if (pkg in messagingPackages) {
            lastDirectMessageTime = now
        }

        if (pkg !in prefs.blockedPackages) {
            // A real window switch to an unguarded app (launcher, messenger, …). If an
            // intervention was up, the user escaped via home — clean up and go idle.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                execute(machine.onForeignPackage(now))
            }
            return
        }

        // While the overlay drives (intervening/exiting), regular events are irrelevant: the
        // machine suppresses them anyway, and skipping saves the tree walk.
        when (machine.state) {
            is SessionStateMachine.State.Intervening,
            is SessionStateMachine.State.Exiting -> return
            else -> Unit
        }

        val root = rootInActiveWindow

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val detection = evaluate(pkg, root, eventClassName = null)
                logDetection(pkg, "scroll", detection)
                execute(machine.onDetection(detection, now))
                if (detection is Detection.ShortForm) {
                    execute(machine.onScroll(pkg, now))
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                maybeDumpTree(pkg, root)
                if (root != null && isDirectMessageScreen(root, pkg)) {
                    lastDirectMessageTime = now
                }
                val className = event.className
                    ?.takeIf { event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }
                val detection = evaluate(pkg, root, className)
                logDetection(pkg, "window", detection)
                execute(machine.onDetection(detection, now))
            }
        }
    }

    private fun evaluate(pkg: String, root: AccessibilityNodeInfo?, eventClassName: CharSequence?): Detection {
        val metrics = resources.displayMetrics
        return engine.evaluate(pkg, root, eventClassName, metrics.widthPixels, metrics.heightPixels)
    }

    private fun execute(actions: List<Action>) {
        for (action in actions) {
            when (action) {
                is Action.ShowIntervention -> showIntervention(action)
                is Action.ExecuteExit -> executeExit(action.pkg)
                Action.PressHome -> performGlobalAction(GLOBAL_ACTION_HOME)
                Action.DismissOverlay -> {
                    handler.removeCallbacks(watchdogRunnable)
                    handler.removeCallbacks(exitCheckRunnable)
                    overlay?.dismiss()
                }
                is Action.ScheduleCheck -> {
                    handler.removeCallbacks(exitCheckRunnable)
                    handler.postDelayed(exitCheckRunnable, action.delayMs)
                }
            }
            DetectionLog.log(
                DetectionLog.Entry(
                    at = System.currentTimeMillis(), pkg = "-", event = "action",
                    state = machine.stateName, decision = action::class.simpleName ?: "?"
                )
            )
        }
    }

    private fun showIntervention(action: Action.ShowIntervention) {
        val relapseTier = prefs.recordSave()
        val persona = prefs.persona
        val request = InterventionRequest(
            persona = persona,
            tier = LineLibrary.tierFor(relapseTier),
            line = LineLibrary.blockLine(persona, relapseTier),
            appName = KnownApps.nameFor(action.pkg) ?: "this app",
            interceptionOrdinal = prefs.savesToday(),
            minutesInAppToday = minutesInAppToday(action.pkg)
        )
        Log.d(TAG, "Intervening in ${action.pkg} (tier=${request.tier}): ${request.line}")
        overlay?.show(request)
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, SessionStateMachine.WATCHDOG_MS)
    }

    private fun minutesInAppToday(pkg: String): Long? {
        val tracker = usageTracker ?: return null
        if (!tracker.isUsageAccessGranted()) return null
        return try {
            tracker.getDetailedUsageStats().firstOrNull { it.packageName == pkg }?.timeSpentMinutes
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Performs the user's configured exit for [pkg]. Every path that can fail falls back to
     * `GLOBAL_ACTION_HOME` — the one exit Android makes unconditionally reliable — so the
     * worst case is always "kicked to the launcher", never "stuck in the feed".
     *
     * Note: never `GLOBAL_ACTION_BACK` here. The overlay is the focused window, so an injected
     * back would be swallowed by our own screen (the original field bug).
     */
    private fun executeExit(pkg: String) {
        val routes = DetectionConfigStore.current.matcherFor(pkg)?.exits
        val configured = prefs.configFor(pkg).exitAction
        val ok = when (configured) {
            ExitAction.LEAVE_APP -> false // handled by the HOME fallback below
            ExitAction.APP_HOME -> launchAppHome(pkg, routes)
            ExitAction.MESSAGES -> launchDeepLink(pkg, routes?.messagesUri)
        }
        if (!ok) performGlobalAction(GLOBAL_ACTION_HOME)
        DetectionLog.log(
            DetectionLog.Entry(
                at = System.currentTimeMillis(), pkg = pkg, event = "exit",
                state = machine.stateName,
                decision = if (ok) "$configured ok" else "$configured -> fallback HOME"
            )
        )
    }

    private fun launchAppHome(pkg: String, routes: ExitRoutes?): Boolean {
        if (routes?.appHome != true) return false
        return try {
            val intent = routes.appHomeUri
                ?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(pkg) }
                ?: packageManager.getLaunchIntentForPackage(pkg) // requires <queries> visibility
                ?: return false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "appHome launch failed for $pkg", e)
            false
        }
    }

    private fun launchDeepLink(pkg: String, uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "deep link launch failed for $pkg ($uri)", e)
            false
        }
    }

    /**
     * The top real application window's root. Never [rootInActiveWindow] during an exit: our
     * focusable overlay IS the active window then, and mistaking it for the foreground app is
     * exactly the bug that made "Leave" a no-op. The overlay reports as an accessibility
     * overlay type, so filtering to application windows excludes it structurally.
     */
    private fun topAppRoot(): AccessibilityNodeInfo? =
        windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .maxByOrNull { it.layer }
            ?.root

    /** The single post-exit verification: is the screen under the overlay still short-form? */
    private fun runExitCheck() {
        val now = System.currentTimeMillis()
        val exiting = machine.state as? SessionStateMachine.State.Exiting ?: return
        val root = topAppRoot()
        val currentPkg = root?.packageName?.toString()
        val detection = if (currentPkg == exiting.pkg) {
            evaluate(exiting.pkg, root, eventClassName = null)
        } else {
            Detection.NoData // left the app entirely — that counts as escaped
        }
        logDetection(exiting.pkg, "exitCheck", detection)
        execute(machine.onExitCheck(detection, now))
    }

    // ---- InterventionHost (called from the overlay UI) ---------------------------------------

    override fun onLeaveRequested() {
        handler.post {
            overlay?.noteLeaving() // the affirmation gets a minimum visible window
            execute(machine.onUserChoseLeave(System.currentTimeMillis()))
        }
    }

    override fun onOverrideCompleted() {
        handler.post { execute(machine.onOverrideCompleted(System.currentTimeMillis())) }
    }

    // ---- diagnostics ---------------------------------------------------------------------------

    private fun logDetection(pkg: String, event: String, detection: Detection) {
        // Positives are always logged; negatives only while diagnostics are on (volume).
        val interesting = detection is Detection.ShortForm || prefs.diagnosticsEnabled
        if (!interesting) return
        // Content-changed storms produce dozens of identical positives per second; unless
        // diagnostics are on, only log when the machine state changed or once per second.
        if (detection is Detection.ShortForm && !prefs.diagnosticsEnabled) {
            val now = System.currentTimeMillis()
            if (machine.stateName == lastLoggedState && now - lastPositiveLogAt < 1000L) return
            lastPositiveLogAt = now
            lastLoggedState = machine.stateName
        }
        DetectionLog.log(
            DetectionLog.Entry(
                at = System.currentTimeMillis(),
                pkg = pkg,
                event = event,
                matchedId = (detection as? Detection.ShortForm)?.matchedId,
                coverage = (detection as? Detection.ShortForm)?.coverage,
                state = machine.stateName,
                decision = detection::class.simpleName ?: "?"
            )
        )
    }

    /**
     * Verbose tree dump for on-device id discovery (`adb logcat -s ZenScan`). Gated behind the
     * diagnostics toggle: walking 2000 nodes every 2s is a real battery cost.
     */
    private fun maybeDumpTree(packageName: String, root: AccessibilityNodeInfo?) {
        if (root == null || !prefs.diagnosticsEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastDumpTime < DUMP_THROTTLE_MS) return
        lastDumpTime = now

        Log.d(SCAN_TAG, "--- window in $packageName ---")
        var visited = 0
        var logged = 0
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && logged < MAX_DUMP_LINES) {
            val (node, depth) = stack.removeLast()
            if (visited++ > MAX_NODES) break
            val id = node.viewIdResourceName
            val text = node.text?.toString()?.takeIf { it.isNotBlank() }
            val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (id != null || text != null || desc != null) {
                Log.d(SCAN_TAG, "id=$id text=$text desc=$desc visible=${node.isVisibleToUser}")
                logged++
            }
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    stack.addLast(child to depth + 1)
                }
            }
        }
    }

    private fun isDirectMessageScreen(node: AccessibilityNodeInfo?, packageName: String, depth: Int = 0): Boolean {
        if (node == null || depth > 8) return false
        val keywords = when (packageName) {
            "com.instagram.android" ->
                listOf("message...", "messages", "direct", "chats", "active now", "write a message")
            "com.snapchat.android" ->
                listOf("chat", "send a chat", "new chat", "friends")
            else -> return false
        }
        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()
        if (text != null && keywords.any { text.contains(it) }) return true
        if (desc != null && keywords.any { desc.contains(it) }) return true
        for (i in 0 until node.childCount) {
            if (isDirectMessageScreen(node.getChild(i), packageName, depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "Zen service interrupted")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlay?.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SCAN_TAG = "ZenScan"
        private const val FRIEND_PASS_WINDOW_MS = 4000L
        private const val MAX_DEPTH = 30
        private const val MAX_NODES = 2000
        private const val DUMP_THROTTLE_MS = 2000L
        private const val MAX_DUMP_LINES = 60
    }
}
