package com.example.zen.data

import kotlinx.serialization.Serializable

/**
 * What happens when a guarded app's short-form feed is intercepted.
 * HARD is reserved: the data model supports it from day one, but only FRICTION is wired in v1.
 */
enum class GuardMode { FRICTION, HARD }

/**
 * Per-app guard settings, keyed by [GuardedApp.key] in [ZenPrefs.appConfigs].
 *
 * [scrollAllowance] is the number of swipes permitted on deliberate entry before the
 * intervention shows. Default 2: entering Reels on purpose shouldn't be punished instantly
 * (that reads as "the app is broken"), but a doom-scroll ramp-up is dozens of swipes.
 * 0 = intervene on entry, for users who want it strict.
 */
@Serializable
data class AppGuardConfig(
    val enabled: Boolean = true,
    val mode: GuardMode = GuardMode.FRICTION,
    val scrollAllowance: Int = DEFAULT_ALLOWANCE
) {
    companion object {
        const val DEFAULT_ALLOWANCE = 2
        const val MAX_ALLOWANCE = 10
    }
}
