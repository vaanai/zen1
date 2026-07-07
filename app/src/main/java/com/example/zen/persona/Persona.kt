package com.example.zen.persona

/**
 * The four personalities. The selected persona reskins the entire app (theme, typography, copy)
 * and drives the voice of the block-interception screen. See [PersonaPalette] for visuals and
 * [LineLibrary] for copy.
 */
enum class Persona(
    val id: String,
    val displayName: String,
    /** One-line pitch shown in the persona chooser. */
    val tagline: String
) {
    GOBLIN(
        id = "GOBLIN",
        displayName = "The Goblin",
        tagline = "Dark, sarcastic, and weirdly motivating. Roasts you off the feed."
    ),
    COACH(
        id = "COACH",
        displayName = "The Coach",
        tagline = "High-energy hype to get you off the feed and into the real game."
    ),
    ZEN(
        id = "ZEN",
        displayName = "Zen",
        tagline = "Calm, quiet, and gently uncompromising."
    ),
    SAGE(
        id = "SAGE",
        displayName = "The Sage",
        tagline = "An old philosopher who is mildly disappointed in you."
    );

    companion object {
        val DEFAULT = GOBLIN

        fun fromId(id: String?): Persona =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
