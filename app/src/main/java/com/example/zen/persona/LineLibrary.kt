package com.example.zen.persona

import kotlin.random.Random

/**
 * Curated, on-device copy for every persona. No network, no LLM — free forever and private.
 *
 * Block lines escalate by "tier", derived from how many times the user has been intercepted today
 * (their relapse count): the persona gets sharper / more pointed the more you come back.
 *
 * ## Tone rules ("funny words on a serious canvas")
 * 1. Humor lives ONLY in headline slots (block lines, welcome, override asides). Buttons,
 *    permissions, settings, and data labels are always plain product English.
 * 2. Block lines: one idea, ≤ 12 words, deadpan over exclamatory. No emoji, ever.
 * 3. Escalation sharpens wit; it never attacks the person — tease the behavior, not the ability.
 * 4. Voices: GOBLIN deadpan sarcasm · COACH tough-love imperatives, zero irony ·
 *    ZEN never jokes, spare and concrete · SAGE literate, dry, mildly disappointed.
 */
object LineLibrary {

    /** Pick a block-interception line for [persona], escalating with today's [relapseCount]. */
    fun blockLine(persona: Persona, relapseCount: Int): String {
        val tier = tierFor(relapseCount)
        val pool = blockLines(persona)[tier] ?: blockLines(persona).getValue(0)
        return pool[Random.nextInt(pool.size)]
    }

    fun welcome(persona: Persona): String = when (persona) {
        Persona.GOBLIN -> "Alright, gremlin. I'll be watching that scroll finger."
        Persona.COACH -> "Let's go! I'm in your corner now. We don't lose to a feed."
        Persona.ZEN -> "I'm here now. Breathe. We'll keep things quiet together."
        Persona.SAGE -> "So. You have enlisted a philosopher to mind your attention. Wise, for once."
    }

    fun shieldTitle(persona: Persona): String = when (persona) {
        Persona.GOBLIN -> "The Goblin is on duty"
        Persona.COACH -> "The Coach is on the clock"
        Persona.ZEN -> "Zen is keeping watch"
        Persona.SAGE -> "The Sage is observing"
    }

    fun shieldDescription(persona: Persona): String = when (persona) {
        Persona.GOBLIN ->
            "Short-form feeds get intercepted. You get roasted, briefly, on the way out."
        Persona.COACH ->
            "Open a feed and you'll get pulled off the bench and back into the game."
        Persona.ZEN ->
            "When the pull to scroll arises, it will be met quietly, and you will be eased away."
        Persona.SAGE ->
            "Idle scrolling will be met with a small, deserved lecture."
    }

    /** Label for the dashboard's headline metric. */
    fun savesLabel(persona: Persona): String = when (persona) {
        Persona.GOBLIN -> "SCROLLS BLOCKED"
        Persona.COACH -> "REPS WON"
        Persona.ZEN -> "MOMENTS RECLAIMED"
        Persona.SAGE -> "TEMPTATIONS RESISTED"
    }

    /** Escalation tier (0..2) for today's relapse count. Public: drives override hold time too. */
    fun tierFor(relapseCount: Int): Int = when {
        relapseCount <= 1 -> 0
        relapseCount <= 3 -> 1
        else -> 2
    }

    /** Shown briefly (≤3 words of grace) while the service navigates out under the overlay. */
    fun leaveAffirmation(persona: Persona): String = when (persona) {
        Persona.GOBLIN -> "Smart move."
        Persona.COACH -> "That's a rep."
        Persona.ZEN -> "Well left."
        Persona.SAGE -> "Wisdom, at last."
    }

    /** Shown while the user is holding the override — the persona watching them do it. */
    fun overrideAside(persona: Persona): String = when (persona) {
        Persona.GOBLIN -> "Oh, we're doing this?"
        Persona.COACH -> "You sure about this play?"
        Persona.ZEN -> "Notice the wanting."
        Persona.SAGE -> "I shall pretend not to watch."
    }

    /** Shown after a completed override, before the overlay clears. */
    fun overrideGranted(persona: Persona): String = when (persona) {
        Persona.GOBLIN -> "Fine. Two minutes."
        Persona.COACH -> "Short break. Then back to work."
        Persona.ZEN -> "A little while, then."
        Persona.SAGE -> "Even Odysseus untied himself eventually."
    }

    private fun blockLines(persona: Persona): Map<Int, List<String>> = when (persona) {
        Persona.GOBLIN -> mapOf(
            0 to listOf(
                "Oh look, the dopamine goblin's back. Put the phone down.",
                "Scrolling already? Bold of you to assume I'd allow that.",
                "Nope. Not today, gremlin."
            ),
            1 to listOf(
                "Again? Your attention span has the structural integrity of a wet wipe.",
                "Truly inspiring commitment to absolutely nothing. Out.",
                "You and this feed need to start seeing other people."
            ),
            2 to listOf(
                "Genuinely impressive dedication to wasting your one wild life.",
                "At this point I'm not even mad, I'm fascinated. Leave.",
                "Your ancestors survived ice ages for THIS? Go. Do something."
            )
        )
        Persona.COACH -> mapOf(
            0 to listOf(
                "Not today, champ. Eyes up — let's move.",
                "Nope, we don't train the doom-scroll muscle here.",
                "Hands off the feed. Go win the day."
            ),
            1 to listOf(
                "That's rep two on the wrong machine. Reset.",
                "Come on, you're better than the feed. Push.",
                "Shake it off and get back to the real game."
            ),
            2 to listOf(
                "Okay, we're benching the phone. Water, a stretch, a win. Go.",
                "Champions log off. Be a champion right now.",
                "Last call — drop the phone, ten pushups, thank me later."
            )
        )
        Persona.ZEN -> mapOf(
            0 to listOf(
                "Let it go. This isn't where your peace lives.",
                "Breathe. The feed can wait; you don't have to.",
                "Gently — set it down."
            ),
            1 to listOf(
                "Notice the pull. Now let it pass.",
                "You returned. That's alright. Return to yourself instead.",
                "Stillness is only one breath away."
            ),
            2 to listOf(
                "The feed is endless; your attention is not. Rest it.",
                "Come back to your breath — again, as many times as it takes.",
                "Nothing here is asking for you. Let it be quiet."
            )
        )
        Persona.SAGE -> mapOf(
            0 to listOf(
                "Ah. You return to the feed. Diogenes lived in a barrel with more restraint.",
                "Seneca wrote on the shortness of life. You are demonstrating it.",
                "The unexamined scroll is not worth scrolling."
            ),
            1 to listOf(
                "Marcus Aurelius ran an empire before breakfast. You have opened Reels. Twice.",
                "The Stoics endured exile. You, it seems, cannot endure mild boredom.",
                "A second attempt. Even Sisyphus was permitted breaks."
            ),
            2 to listOf(
                "We have reached the part of the dialogue where Socrates simply asks you to leave.",
                "I begin to suspect the allegory of the cave was about you, specifically.",
                "Persistence is a virtue. You have found the one place it is not."
            )
        )
    }
}
