package com.example.zen.persona

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.zen.ui.design.FrauncesFamily
import com.example.zen.ui.design.InterFamily

/**
 * Single source of truth for a persona's visual identity. Used by both the Compose UI
 * (via PersonaTheme) and the Accessibility Service overlay (via [Color.toArgb]).
 */
data class PersonaColors(
    /** Background gradient, top → bottom (3 stops). */
    val gradient: List<Color>,
    val accent: Color,
    val accentSecondary: Color,
    val danger: Color,
    val safe: Color,
    val warn: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val fontFamily: FontFamily,
    /** True for light-background personas (Zen, Sage) — affects status-bar icon tint, etc. */
    val isLight: Boolean
)

object PersonaPalette {

    fun of(persona: Persona): PersonaColors = when (persona) {
        Persona.GOBLIN -> Goblin
        Persona.COACH -> Coach
        Persona.ZEN -> Zen
        Persona.SAGE -> Sage
    }

    // Goblin — the original "cosmic" dark/neon look. Ramp kept tight: atmosphere, not a wash.
    private val Goblin = PersonaColors(
        gradient = listOf(Color(0xFF07050E), Color(0xFF0B091B), Color(0xFF141030)),
        accent = Color(0xFF8B5CF6),          // neon violet
        accentSecondary = Color(0xFF06B6D4), // cyber cyan
        danger = Color(0xFFEF4444),
        safe = Color(0xFF10B981),
        warn = Color(0xFFF59E0B),
        textPrimary = Color(0xFFF9FAFB),
        textSecondary = Color(0xFF9CA3AF),
        cardBackground = Color(0x15FFFFFF),
        cardBorder = Color(0x1CFFFFFF),
        fontFamily = InterFamily,
        isLight = false
    )

    // Coach — dark athletic base, electric lime + energetic orange. Tight ramp (see Goblin).
    private val Coach = PersonaColors(
        gradient = listOf(Color(0xFF0A0F0A), Color(0xFF0E1A0A), Color(0xFF13200D)),
        accent = Color(0xFFB6FF3C),          // electric lime
        accentSecondary = Color(0xFFFF6B35), // energetic orange
        danger = Color(0xFFFF4D4D),
        safe = Color(0xFFB6FF3C),
        warn = Color(0xFFFFC53D),
        textPrimary = Color(0xFFF7FFF0),
        textSecondary = Color(0xFFA9BE9C),
        cardBackground = Color(0x14FFFFFF),
        cardBorder = Color(0x22B6FF3C),
        fontFamily = InterFamily,
        isLight = false
    )

    // Zen — calm, rich, light beige.
    private val Zen = PersonaColors(
        gradient = listOf(Color(0xFFF4EEE2), Color(0xFFEBE1CF), Color(0xFFE1D5BE)),
        accent = Color(0xFF7C8C6B),          // sage green
        accentSecondary = Color(0xFFB08968), // warm clay
        danger = Color(0xFFB5705B),
        safe = Color(0xFF7C8C6B),
        warn = Color(0xFFC19A5B),
        textPrimary = Color(0xFF3A352C),
        textSecondary = Color(0xFF8A8273),
        cardBackground = Color(0x0F3A352C),
        cardBorder = Color(0x1A3A352C),
        fontFamily = InterFamily,
        isLight = true
    )

    // Sage — parchment & ink, serif, old-world.
    private val Sage = PersonaColors(
        gradient = listOf(Color(0xFFEEE5D4), Color(0xFFE4D8C1), Color(0xFFD9CAAE)),
        accent = Color(0xFF6B4F3A),          // ink brown
        accentSecondary = Color(0xFF8C6A43), // aged bronze
        danger = Color(0xFF8A4B3A),
        safe = Color(0xFF5E6B47),
        warn = Color(0xFFA9803F),
        textPrimary = Color(0xFF2E2519),
        textSecondary = Color(0xFF7A6A52),
        cardBackground = Color(0x122E2519),
        cardBorder = Color(0x222E2519),
        fontFamily = FrauncesFamily,
        isLight = true
    )
}
