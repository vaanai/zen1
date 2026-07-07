package com.example.zen.data

import kotlinx.serialization.Serializable

/**
 * What happens when a guarded app's short-form feed is intercepted.
 * HARD is reserved: the data model supports it from day one, but only FRICTION is wired in v1.
 */
enum class GuardMode { FRICTION, HARD }

/**
 * Where "Leave" takes the user. LEAVE_APP (launcher home) is the only exit Android makes
 * unconditionally reliable, so it is both the default and the runtime fallback for the others.
 */
@Serializable
enum class ExitAction { LEAVE_APP, APP_HOME, MESSAGES }

/**
 * Per-app guard settings, keyed by [GuardedApp.key] in [ZenPrefs.appConfigs].
 *
 * [scrollAllowance] is the number of swipes permitted on deliberate entry before the
 * intervention shows. Default 2: entering Reels on purpose shouldn't be punished instantly
 * (that reads as "the app is broken"), but a doom-scroll ramp-up is dozens of swipes.
 * 0 = intervene on entry, for users who want it strict.
 *
 * [friendPass] allows the one video a friend DM'd you (block on the first scroll past it) —
 * per-app because "my friend sent me something" is the #1 excuse for disabling a blocker.
 */
@Serializable
data class AppGuardConfig(
    val enabled: Boolean = true,
    val mode: GuardMode = GuardMode.FRICTION,
    val scrollAllowance: Int = DEFAULT_ALLOWANCE,
    val exitAction: ExitAction = ExitAction.LEAVE_APP,
    val friendPass: Boolean = true
) {
    companion object {
        const val DEFAULT_ALLOWANCE = 2
        const val MAX_ALLOWANCE = 10
    }
}
