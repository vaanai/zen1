package com.example.zen.detection

import kotlinx.serialization.Serializable

/**
 * Data-driven matchers for short-form surfaces. Detection identifiers live here — as data, not
 * code — so an Instagram update that renames a view id can be fixed by shipping a new JSON to
 * the repo (fetched remotely) instead of a new APK.
 *
 * Matching semantics:
 *  - [AppMatcher.viewIds] are resource **entry names** (`clips_viewer_view_pager`), compared
 *    case-insensitively for **exact equality** against the node's
 *    `viewIdResourceName.substringAfterLast(":id/")`. Never bare substring: Instagram's home
 *    feed embeds inline reels units whose ids merely *contain* "clips"/"reel", and substring
 *    matching is exactly the bug that made the whole app read as short-form.
 *  - [AppMatcher.requireVisible]: the matched node must be `isVisibleToUser`.
 *  - [AppMatcher.minCoverage]: the matched node's on-screen bounds must cover at least this
 *    fraction of the display — a full-screen viewer, not an inline preview tile.
 *  - [AppMatcher.negativeViewIds]: hotfix escape hatch — if any is present in the tree the
 *    screen is forced non-short-form, deployable via remote config without an app update.
 *  - [AppMatcher.classNameHints]: optional corroboration against a window-state-changed event's
 *    class name; shortens the confirmation debounce but is never required.
 *  - [AppMatcher.texts] (only with [MatchStrategy.VIEW_ID_OR_TEXT]): last-resort text match for
 *    apps with obfuscated ids; the text node must still pass the visibility + coverage gates,
 *    so a nav-bar label can never trigger it.
 */
@Serializable
data class DetectionConfig(
    val schemaVersion: Int,
    val configVersion: Int,
    val updatedAt: String = "",
    val apps: List<AppMatcher> = emptyList()
) {
    private val byPackage: Map<String, AppMatcher> by lazy {
        apps.flatMap { m -> m.packages.map { it to m } }.toMap()
    }

    fun matcherFor(pkg: String): AppMatcher? = byPackage[pkg]

    fun isValid(): Boolean =
        schemaVersion == SUPPORTED_SCHEMA_VERSION &&
            configVersion > 0 &&
            apps.isNotEmpty() &&
            apps.all { it.packages.isNotEmpty() && it.key.isNotBlank() }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

@Serializable
enum class MatchStrategy { VIEW_ID, VIEW_ID_OR_TEXT, WHOLE_APP }

@Serializable
data class AppMatcher(
    val key: String,
    val packages: List<String>,
    val strategy: MatchStrategy,
    val viewIds: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
    val minCoverage: Float = 0.55f,
    val requireVisible: Boolean = true,
    val classNameHints: List<String> = emptyList(),
    val negativeViewIds: List<String> = emptyList()
) {
    val viewIdsLower: Set<String> by lazy { viewIds.map { it.lowercase() }.toSet() }
    val textsLower: Set<String> by lazy { texts.map { it.lowercase() }.toSet() }
    val negativeViewIdsLower: Set<String> by lazy { negativeViewIds.map { it.lowercase() }.toSet() }
}
