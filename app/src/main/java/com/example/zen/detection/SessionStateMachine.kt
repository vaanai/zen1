package com.example.zen.detection

/**
 * Session/intervention state machine. Pure Kotlin — the service feeds it detections, scrolls,
 * and user choices along with the current time; it returns the actions to perform.
 *
 * Why a state machine: the v1.1 bug class was *stateless re-triggering* — every accessibility
 * event re-ran detection, every detection re-blocked, and a back-press that landed on another
 * matching screen re-blocked again, trapping the user in a loop. Here that is impossible by
 * construction:
 *
 *  - [State.Intervening] hard-suppresses all detections while the overlay is up.
 *  - [State.Exiting] owns navigation: one deterministic exit (the service resolves the user's
 *    configured [com.example.zen.data.ExitAction] — launcher home, the app's own home, or its
 *    DM inbox), then one windows-based verification; if the short-form surface somehow
 *    survived, HOME fires as the universal fallback. The overlay masks the whole transition.
 *  - [State.Cooldown] absorbs transient matches on whatever screen the exit lands on.
 *  - [State.Confirming] debounces one-frame matches during screen transitions.
 *
 * ```
 * Idle -> Confirming -> InShortForm -> Intervening -> Exiting -> Cooldown -> Idle
 *                                            \-> OverridePass -> Idle
 * ```
 */
class SessionStateMachine(
    /** Scrolls permitted before intervening (already including any earned/lenient bonus). */
    private val allowanceFor: (String) -> Int,
    /** Whether a Friend Pass (arrived from a DM) is currently active for this app. */
    private val friendPassActive: (String) -> Boolean
) {

    sealed interface State {
        data object Idle : State
        data class Confirming(val pkg: String, val firstSeenAt: Long) : State
        data class InShortForm(val pkg: String, val scrolls: Int, val friendPass: Boolean) : State
        data class Intervening(val pkg: String, val shownAt: Long) : State
        data class Exiting(val pkg: String, val startedAt: Long) : State
        data class Cooldown(val pkg: String, val until: Long) : State
        data class OverridePass(val pkg: String, val until: Long) : State
    }

    sealed interface Action {
        /** Show the friction screen. The service enriches this with persona/line/tier. */
        data class ShowIntervention(val pkg: String, val scrollsUsed: Int, val allowance: Int) : Action
        /** Perform the user's configured exit for [pkg] (service resolves which one). */
        data class ExecuteExit(val pkg: String) : Action
        data object PressHome : Action
        data object DismissOverlay : Action
        /** Re-evaluate detection after [delayMs] and feed the result to [onExitCheck]. */
        data class ScheduleCheck(val delayMs: Long) : Action
    }

    var state: State = State.Idle
        private set

    /**
     * Last time a scroll was *counted*. Instagram fires TYPE_VIEW_SCROLLED in storms (progress
     * bars, list settling) — dozens per second — so raw events would exhaust any allowance in
     * milliseconds. A human swipe happens at most ~1/sec; counting one scroll per
     * [SCROLL_DEBOUNCE_MS] makes the allowance mean what it says. Initialized on feed entry so
     * the landing settle-storm never counts as the first swipe.
     */
    private var lastScrollCountedAt = 0L

    val stateName: String get() = state::class.simpleName ?: "?"

    /** A detection result from a regular accessibility event. */
    fun onDetection(detection: Detection, now: Long): List<Action> {
        expire(now)
        if (detection !is Detection.ShortForm) {
            if (detection is Detection.NotShortForm) onNegative(detection.pkg)
            return emptyList()
        }

        val pkg = detection.pkg
        return when (val s = state) {
            is State.Idle ->
                if (detection.corroborated) enter(pkg, now) else { state = State.Confirming(pkg, now); emptyList() }

            is State.Confirming ->
                if (s.pkg != pkg) { state = State.Confirming(pkg, now); emptyList() }
                else if (detection.corroborated || now - s.firstSeenAt >= CONFIRM_MS) enter(pkg, now)
                else emptyList()

            is State.InShortForm ->
                if (s.pkg != pkg) { state = State.Confirming(pkg, now); emptyList() } else emptyList()

            // Hard suppression: the overlay is up or we are navigating out.
            is State.Intervening, is State.Exiting -> emptyList()

            is State.Cooldown ->
                if (s.pkg == pkg) emptyList() else { state = State.Confirming(pkg, now); emptyList() }

            is State.OverridePass ->
                if (s.pkg == pkg) emptyList() else { state = State.Confirming(pkg, now); emptyList() }
        }
    }

    /** A scroll event in [pkg] whose window currently evaluates short-form. */
    fun onScroll(pkg: String, now: Long): List<Action> {
        expire(now)
        val s = state
        if (s !is State.InShortForm || s.pkg != pkg) return emptyList()
        if (now - lastScrollCountedAt < SCROLL_DEBOUNCE_MS) return emptyList()
        lastScrollCountedAt = now
        val scrolls = s.scrolls + 1
        val allowance = if (s.friendPass) 0 else allowanceFor(pkg)
        return if (scrolls > allowance) {
            intervene(pkg, scrolls, allowance, now)
        } else {
            state = s.copy(scrolls = scrolls)
            emptyList()
        }
    }

    /** A window-state change to a package we don't guard (launcher, another app). */
    fun onForeignPackage(now: Long): List<Action> {
        expire(now)
        return when (state) {
            // User escaped on their own (home gesture, app switch): clean up.
            is State.Intervening, is State.Exiting -> {
                state = State.Idle
                listOf(Action.DismissOverlay)
            }
            is State.OverridePass -> emptyList() // pass survives brief app switches
            else -> {
                state = State.Idle
                emptyList()
            }
        }
    }

    /** User tapped Leave (or pressed back) on the friction screen. */
    fun onUserChoseLeave(now: Long): List<Action> {
        val s = state as? State.Intervening ?: return emptyList()
        state = State.Exiting(s.pkg, now)
        // Overlay stays up (showing the leave affirmation) and masks the navigation under it.
        return listOf(Action.ExecuteExit(s.pkg), Action.ScheduleCheck(EXIT_CHECK_MS))
    }

    /** User completed the hold-to-continue override. */
    fun onOverrideCompleted(now: Long): List<Action> {
        val s = state as? State.Intervening ?: return emptyList()
        state = State.OverridePass(s.pkg, now + OVERRIDE_PASS_MS)
        return listOf(Action.DismissOverlay)
    }

    /**
     * Result of the single [Action.ScheduleCheck] verification after an exit. One check, one
     * decision: escaped → dismiss; still on the short-form surface → HOME (guaranteed escape),
     * then dismiss. Either way the session enters [State.Cooldown].
     */
    fun onExitCheck(detection: Detection, now: Long): List<Action> {
        val s = state as? State.Exiting ?: return emptyList()
        state = State.Cooldown(s.pkg, now + COOLDOWN_MS)
        return if (detection is Detection.ShortForm && detection.pkg == s.pkg) {
            listOf(Action.PressHome, Action.DismissOverlay)
        } else {
            listOf(Action.DismissOverlay)
        }
    }

    /** Safety net: an intervention nobody interacted with for [WATCHDOG_MS] leaves on its own. */
    fun onWatchdog(now: Long): List<Action> {
        val s = state as? State.Intervening ?: return emptyList()
        return if (now - s.shownAt >= WATCHDOG_MS) onUserChoseLeave(now) else emptyList()
    }

    fun reset() {
        state = State.Idle
    }

    // ---- internals -------------------------------------------------------------------------

    private fun enter(pkg: String, now: Long): List<Action> {
        val friendPass = friendPassActive(pkg)
        val allowance = if (friendPass) 0 else allowanceFor(pkg)
        lastScrollCountedAt = now // the landing settle-storm must not count as a swipe
        state = State.InShortForm(pkg, scrolls = 0, friendPass = friendPass)
        // Direct entry with zero allowance intervenes immediately; a Friend Pass lets the
        // landed video play and intervenes on the first scroll past it.
        return if (!friendPass && allowance == 0) intervene(pkg, 0, 0, now) else emptyList()
    }

    private fun intervene(pkg: String, scrolls: Int, allowance: Int, now: Long): List<Action> {
        state = State.Intervening(pkg, now)
        return listOf(Action.ShowIntervention(pkg, scrolls, allowance))
    }

    private fun onNegative(pkg: String) {
        when (val s = state) {
            is State.Confirming -> if (s.pkg == pkg) state = State.Idle
            is State.InShortForm -> if (s.pkg == pkg) state = State.Idle
            else -> Unit // Intervening/Exiting suppressed; Cooldown/OverridePass expire on their own
        }
    }

    private fun expire(now: Long) {
        when (val s = state) {
            is State.Cooldown -> if (now >= s.until) state = State.Idle
            is State.OverridePass -> if (now >= s.until) state = State.Idle
            else -> Unit
        }
    }

    companion object {
        const val CONFIRM_MS = 400L
        const val SCROLL_DEBOUNCE_MS = 500L
        const val EXIT_CHECK_MS = 1200L
        const val COOLDOWN_MS = 4000L
        const val OVERRIDE_PASS_MS = 2 * 60 * 1000L
        const val WATCHDOG_MS = 30_000L
    }
}
