package com.example.zen.detection

import com.example.zen.detection.SessionStateMachine.Action
import com.example.zen.detection.SessionStateMachine.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateMachineTest {

    private val IG = "com.instagram.android"

    private fun machine(
        allowance: Int = 2,
        friendPass: Boolean = false
    ) = SessionStateMachine(allowanceFor = { allowance }, friendPassActive = { _ -> friendPass })

    private fun shortForm(pkg: String = IG, corroborated: Boolean = false) =
        Detection.ShortForm(pkg, "clips_viewer_view_pager", 0.9f, corroborated)

    private fun notShortForm(pkg: String = IG) = Detection.NotShortForm(pkg)

    /** Drive Idle → InShortForm through the confirmation debounce. */
    private fun SessionStateMachine.confirm(pkg: String = IG, at: Long = 0L): List<Action> {
        onDetection(shortForm(pkg), at)
        return onDetection(shortForm(pkg), at + SessionStateMachine.CONFIRM_MS)
    }

    // ---- confirmation debounce -----------------------------------------------------------

    @Test
    fun `single transient match does not enter short-form`() {
        val m = machine()
        m.onDetection(shortForm(), 0L)
        assertTrue(m.state is State.Confirming)
        // Detection goes negative before CONFIRM_MS elapses: back to Idle.
        m.onDetection(notShortForm(), 100L)
        assertEquals(State.Idle, m.state)
    }

    @Test
    fun `sustained match enters short-form after debounce`() {
        val m = machine()
        m.confirm()
        assertTrue(m.state is State.InShortForm)
    }

    @Test
    fun `corroborated match enters immediately`() {
        val m = machine()
        m.onDetection(shortForm(corroborated = true), 0L)
        assertTrue(m.state is State.InShortForm)
    }

    // ---- allowance & intervention --------------------------------------------------------

    @Test
    fun `intervenes after allowance exceeded`() {
        val m = machine(allowance = 2)
        m.confirm()
        // Human-speed swipes: one per second.
        assertTrue(m.onScroll(IG, 1400L).isEmpty())  // 1
        assertTrue(m.onScroll(IG, 2400L).isEmpty())  // 2
        val actions = m.onScroll(IG, 3400L)          // 3 > 2 → intervene
        assertEquals(listOf<Action>(Action.ShowIntervention(IG, 3, 2)), actions)
        assertTrue(m.state is State.Intervening)
    }

    @Test
    fun `scroll event storms count as one swipe`() {
        val m = machine(allowance = 2)
        m.confirm() // entry at t=CONFIRM_MS; the landing settle-storm must not count
        // Instagram fires TYPE_VIEW_SCROLLED dozens of times per second while settling.
        repeat(50) { i -> m.onScroll(IG, SessionStateMachine.CONFIRM_MS + i * 10L) }
        assertTrue(m.state is State.InShortForm)
        assertEquals(0, (m.state as State.InShortForm).scrolls) // all within the debounce window
        // One real swipe later counts exactly once despite its own event storm.
        repeat(20) { i -> m.onScroll(IG, 2000L + i * 10L) }
        assertEquals(1, (m.state as State.InShortForm).scrolls)
    }

    @Test
    fun `zero allowance intervenes on entry`() {
        val m = machine(allowance = 0)
        val actions = m.confirm()
        assertEquals(listOf<Action>(Action.ShowIntervention(IG, 0, 0)), actions)
        assertTrue(m.state is State.Intervening)
    }

    @Test
    fun `friend pass allows landed video then intervenes on first scroll`() {
        val m = machine(allowance = 5, friendPass = true)
        val entryActions = m.confirm()
        assertTrue(entryActions.isEmpty()) // no block on entry despite effective allowance 0
        val actions = m.onScroll(IG, 1500L)
        assertEquals(listOf<Action>(Action.ShowIntervention(IG, 1, 0)), actions)
    }

    // ---- suppression while intervening (the old re-block loop) ----------------------------

    @Test
    fun `detections are hard-suppressed while intervening`() {
        val m = machine(allowance = 0)
        m.confirm()
        assertTrue(m.state is State.Intervening)
        // The v1.1 bug: content-changed storms re-triggered blocks. Now: no actions, no state change.
        repeat(10) { i ->
            assertTrue(m.onDetection(shortForm(), 1000L + i).isEmpty())
            assertTrue(m.onScroll(IG, 1000L + i).isEmpty())
        }
        assertTrue(m.state is State.Intervening)
    }

    // ---- leave & exit escalation -----------------------------------------------------------

    @Test
    fun `leave executes exit and schedules one verification`() {
        val m = machine(allowance = 0)
        m.confirm()
        val actions = m.onUserChoseLeave(2000L)
        assertEquals(
            listOf(Action.ExecuteExit(IG), Action.ScheduleCheck(SessionStateMachine.EXIT_CHECK_MS)),
            actions
        )
        assertTrue(m.state is State.Exiting)
    }

    @Test
    fun `second leave request while exiting is ignored`() {
        val m = machine(allowance = 0)
        m.confirm()
        m.onUserChoseLeave(2000L)
        assertTrue(m.onUserChoseLeave(2100L).isEmpty())
        assertTrue(m.state is State.Exiting)
    }

    @Test
    fun `exit check when not exiting is ignored`() {
        val m = machine(allowance = 2)
        m.confirm()
        assertTrue(m.onExitCheck(notShortForm(), 1000L).isEmpty())
        assertTrue(m.state is State.InShortForm)
    }

    @Test
    fun `successful escape dismisses and enters cooldown`() {
        val m = machine(allowance = 0)
        m.confirm()
        m.onUserChoseLeave(2000L)
        val actions = m.onExitCheck(notShortForm(), 3200L)
        assertEquals(listOf<Action>(Action.DismissOverlay), actions)
        assertTrue(m.state is State.Cooldown)
    }

    @Test
    fun `exit check still short-form falls back to home`() {
        val m = machine(allowance = 0)
        m.confirm()
        m.onUserChoseLeave(2000L)
        // The configured exit somehow left the user on the feed → HOME. Guaranteed escape.
        assertEquals(
            listOf(Action.PressHome, Action.DismissOverlay),
            m.onExitCheck(shortForm(), 3200L)
        )
        assertTrue(m.state is State.Cooldown)
    }

    @Test
    fun `cooldown absorbs residual matches then expires`() {
        val m = machine(allowance = 0)
        m.confirm()
        m.onUserChoseLeave(2000L)
        m.onExitCheck(notShortForm(), 3200L) // → Cooldown until 3200 + 4000
        // Transient match on the landing screen: ignored.
        assertTrue(m.onDetection(shortForm(), 4000L).isEmpty())
        assertTrue(m.state is State.Cooldown)
        // After expiry, detection is live again (goes to Confirming).
        m.onDetection(shortForm(), 3200L + SessionStateMachine.COOLDOWN_MS + 1)
        assertTrue(m.state is State.Confirming)
    }

    // ---- override -------------------------------------------------------------------------

    @Test
    fun `override grants a pass and suppresses detection until expiry`() {
        val m = machine(allowance = 0)
        m.confirm()
        val actions = m.onOverrideCompleted(5000L)
        assertEquals(listOf<Action>(Action.DismissOverlay), actions)
        assertTrue(m.state is State.OverridePass)
        // Within the pass: nothing triggers.
        assertTrue(m.onDetection(shortForm(), 60_000L).isEmpty())
        assertTrue(m.onScroll(IG, 61_000L).isEmpty())
        // After expiry: detection resumes.
        m.onDetection(shortForm(), 5000L + SessionStateMachine.OVERRIDE_PASS_MS + 1)
        assertTrue(m.state is State.Confirming)
    }

    // ---- foreign package / home escape ------------------------------------------------------

    @Test
    fun `leaving to another app while intervening dismisses overlay`() {
        val m = machine(allowance = 0)
        m.confirm()
        val actions = m.onForeignPackage(3000L)
        assertEquals(listOf<Action>(Action.DismissOverlay), actions)
        assertEquals(State.Idle, m.state)
    }

    @Test
    fun `override pass survives brief app switches`() {
        val m = machine(allowance = 0)
        m.confirm()
        m.onOverrideCompleted(5000L)
        m.onForeignPackage(10_000L)
        assertTrue(m.state is State.OverridePass)
    }

    // ---- watchdog ---------------------------------------------------------------------------

    @Test
    fun `watchdog leaves after timeout but not before`() {
        val m = machine(allowance = 0)
        m.confirm(at = 0L)
        assertTrue(m.onWatchdog(SessionStateMachine.CONFIRM_MS + 100).isEmpty())
        assertTrue(m.state is State.Intervening)
        val actions = m.onWatchdog(SessionStateMachine.CONFIRM_MS + SessionStateMachine.WATCHDOG_MS + 1)
        assertTrue(actions.any { it is Action.ExecuteExit })
        assertTrue(m.state is State.Exiting)
    }

    // ---- NotShortForm resets ----------------------------------------------------------------

    @Test
    fun `leaving the feed resets the session`() {
        val m = machine(allowance = 2)
        m.confirm()
        m.onScroll(IG, 500L)
        m.onDetection(notShortForm(), 1000L)
        assertEquals(State.Idle, m.state)
        // Re-entering starts a fresh session with a fresh allowance.
        m.confirm(at = 2000L)
        assertTrue(m.onScroll(IG, 3000L).isEmpty())
    }
}
