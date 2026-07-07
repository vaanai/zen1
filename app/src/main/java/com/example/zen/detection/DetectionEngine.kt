package com.example.zen.detection

import android.view.accessibility.AccessibilityNodeInfo

/** Outcome of evaluating one accessibility snapshot against the detection config. */
sealed interface Detection {
    /** The active window is a short-form surface. */
    data class ShortForm(
        val pkg: String,
        val matchedId: String?,
        val coverage: Float,
        val corroborated: Boolean
    ) : Detection

    /** The active window in a guarded app is NOT short-form. */
    data class NotShortForm(val pkg: String) : Detection

    /** Nothing to evaluate: null root, unguarded/unknown package. */
    data object NoData : Detection
}

/**
 * Pure per-event evaluation: does this window contain the *active, full-screen* short-form
 * viewer? All fuzziness (debounce, cooldowns, suppression) lives in [SessionStateMachine];
 * this class only answers the question for the snapshot it is given.
 */
class DetectionEngine(private val config: () -> DetectionConfig) {

    fun evaluate(
        pkg: String,
        root: AccessibilityNodeInfo?,
        eventClassName: CharSequence?,
        displayWidth: Int,
        displayHeight: Int
    ): Detection {
        val matcher = config().matcherFor(pkg) ?: return Detection.NoData
        if (matcher.strategy == MatchStrategy.WHOLE_APP) {
            return Detection.ShortForm(pkg, matchedId = null, coverage = 1f, corroborated = true)
        }
        if (root == null) return Detection.NoData

        val corroborated = eventClassName != null && matcher.classNameHints.any {
            eventClassName.toString().contains(it, ignoreCase = true)
        }

        val displayArea = displayWidth.toFloat() * displayHeight.toFloat()
        if (displayArea <= 0f) return Detection.NoData

        var positive: Match? = null
        var negative = false

        walk(root) { node ->
            val idEntry = node.viewIdResourceName
                ?.substringAfterLast(":id/")
                ?.lowercase()

            if (idEntry != null && idEntry in matcher.negativeViewIdsLower) {
                negative = true
                return@walk true // short-circuit: negative always wins
            }

            val idHit = idEntry != null && idEntry in matcher.viewIdsLower
            val textHit = !idHit && matcher.strategy == MatchStrategy.VIEW_ID_OR_TEXT &&
                nodeTextMatches(node, matcher)

            if ((idHit || textHit) && positive == null && passesGates(node, matcher, displayArea)) {
                positive = Match(idEntry ?: node.text?.toString(), coverageOf(node, displayArea))
                // Keep walking: a negative id later in the tree must still be able to veto.
                return@walk matcher.negativeViewIdsLower.isEmpty()
            }
            false
        }

        if (negative) return Detection.NotShortForm(pkg)
        val match = positive ?: return Detection.NotShortForm(pkg)
        return Detection.ShortForm(pkg, match.id, match.coverage, corroborated)
    }

    private data class Match(val id: String?, val coverage: Float)

    private fun nodeTextMatches(node: AccessibilityNodeInfo, matcher: AppMatcher): Boolean {
        if (matcher.textsLower.isEmpty()) return false
        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()
        return matcher.textsLower.any { needle ->
            (text != null && text.contains(needle)) || (desc != null && desc.contains(needle))
        }
    }

    private fun passesGates(node: AccessibilityNodeInfo, matcher: AppMatcher, displayArea: Float): Boolean {
        if (matcher.requireVisible && !node.isVisibleToUser) return false
        return coverageOf(node, displayArea) >= matcher.minCoverage
    }

    private fun coverageOf(node: AccessibilityNodeInfo, displayArea: Float): Float {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val area = bounds.width().coerceAtLeast(0).toFloat() * bounds.height().coerceAtLeast(0).toFloat()
        return (area / displayArea).coerceIn(0f, 1f)
    }

    /**
     * Depth- and count-bounded DFS. [visit] returns true to short-circuit the walk.
     */
    private fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Boolean) {
        var visited = 0
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (visited++ > MAX_NODES) return
            if (visit(node)) return
            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    stack.addLast(child to depth + 1)
                }
            }
        }
    }

    companion object {
        private const val MAX_DEPTH = 30
        private const val MAX_NODES = 2000
    }
}
