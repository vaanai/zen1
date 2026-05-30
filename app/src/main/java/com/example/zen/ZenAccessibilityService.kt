package com.example.zen

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class ZenAccessibilityService : AccessibilityService() {

    private val TAG = "ZenBlocker"

    private val targetPackages = listOf(
        "com.instagram.android", // Instagram
        "com.google.android.youtube", // YouTube
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (some regions)
        "com.snapchat.android" // Snapchat
    )

    private val messagingPackages = listOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "com.facebook.orca", // Messenger
        "com.discord",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging"
    )

    private var lastDirectMessageTime = 0L
    private var isFriendPassActive = false
    private var firstReelSignature = ""
    private var lastBlockTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 1. If user is in a known external messaging app, record DM time
        if (messagingPackages.contains(packageName)) {
            lastDirectMessageTime = System.currentTimeMillis()
        }

        // We only care about our target apps for blocking and internal DM tracking
        if (!targetPackages.contains(packageName)) return

        // 2. Cooldown check: if a block occurred recently, skip checks to allow screen transitions to settle
        if (System.currentTimeMillis() - lastBlockTime < 1500) {
            return
        }

        val rootNode = rootInActiveWindow

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleScrollEvent(packageName, event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (rootNode != null) {
                    // Update DM time if user is inside Instagram or Snapchat DM screens
                    if (packageName == "com.instagram.android" || packageName == "com.snapchat.android") {
                        if (isDirectMessageScreen(rootNode, packageName)) {
                            lastDirectMessageTime = System.currentTimeMillis()
                            // If they are back in DM, naturally reset the friend pass
                            if (isFriendPassActive) {
                                Log.d(TAG, "User returned to DMs. Resetting Friend Pass.")
                                isFriendPassActive = false
                                firstReelSignature = ""
                            }
                        }
                    }

                    // Dynamically capture the signature once the video elements populate
                    if (isFriendPassActive && firstReelSignature.isBlank()) {
                        val currentSig = getScreenSignature(rootNode)
                        if (currentSig.isNotBlank()) {
                            firstReelSignature = currentSig
                            Log.d(TAG, "Friend Pass signature initialized: $firstReelSignature")
                        }
                    }

                    // If we have an active pass but navigated away from short-form content entirely
                    if (isFriendPassActive && !isShortFormContent(rootNode)) {
                        Log.d(TAG, "Navigated away from short-form content. Resetting Friend Pass.")
                        isFriendPassActive = false
                        firstReelSignature = ""
                    }
                }
            }
        }
    }

    private fun handleScrollEvent(packageName: String, event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // TikTok is exclusively short-form, so treat the entire package as such
        if (packageName.contains("zhiliaoapp") || packageName.contains("ugc.trill")) {
            handleShortFormBlock("TikTok", rootNode)
            return
        }

        // For others, check if the current screen contains indicators of short-form content (Reels/Shorts/Spotlight)
        if (isShortFormContent(rootNode)) {
            handleShortFormBlock(packageName, rootNode)
        }
    }

    private fun handleShortFormBlock(packageName: String, rootNode: AccessibilityNodeInfo) {
        val currentSignature = getScreenSignature(rootNode)

        if (isFriendPassActive) {
            // If the pass is active, wait for the user to scroll to a DIFFERENT video
            if (firstReelSignature.isNotBlank() && currentSignature.isNotBlank() && !signaturesMatch(firstReelSignature, currentSignature)) {
                Log.d(TAG, "Friend Pass scroll detected in $packageName. Blocking and backing out.")
                blockScroll(packageName)
            }
        } else {
            // Check if we recently came from a messaging/DM application
            val timeSinceDM = System.currentTimeMillis() - lastDirectMessageTime
            if (timeSinceDM < 4000) {
                isFriendPassActive = true
                firstReelSignature = currentSignature
                Log.d(TAG, "Friend Pass activated in $packageName. Signature: $firstReelSignature")
            } else {
                Log.d(TAG, "Direct short-form access in $packageName (no active pass). Blocking.")
                blockScroll(packageName)
            }
        }
    }

    private fun blockScroll(appName: String) {
        Log.d(TAG, "Blocked doom-scroll in $appName")
        lastBlockTime = System.currentTimeMillis() // Set cooldown to prevent loops
        RoastEngine.triggerRoast(this) // Roast the user ruthlessly
        // Perform a global back action to return the user to the DMs or prior screen
        performGlobalAction(GLOBAL_ACTION_BACK)
        // Reset pass state
        isFriendPassActive = false
        firstReelSignature = ""
    }

    private fun isShortFormContent(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 8) return false

        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()

        // Keywords that heavily indicate we are in the addictive short-form feed
        val indicators = listOf("reels", "shorts", "spotlight", "for you")

        if (text != null && indicators.any { text.contains(it) }) return true
        if (desc != null && indicators.any { desc.contains(it) }) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isShortFormContent(child, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun isDirectMessageScreen(node: AccessibilityNodeInfo?, packageName: String, depth: Int = 0): Boolean {
        if (node == null || depth > 8) return false

        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()

        if (packageName == "com.instagram.android") {
            // Instagram DM indicators: message input box, Direct header, or messages keywords
            val dmKeywords = listOf("message...", "messages", "direct", "chats", "active now", "write a message")
            if (text != null && dmKeywords.any { text.contains(it) }) return true
            if (desc != null && dmKeywords.any { desc.contains(it) }) return true
        }

        if (packageName == "com.snapchat.android") {
            // Snapchat DM/Chat screen indicators
            val dmKeywords = listOf("chat", "send a chat", "new chat", "friends")
            if (text != null && dmKeywords.any { text.contains(it) }) return true
            if (desc != null && dmKeywords.any { desc.contains(it) }) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (isDirectMessageScreen(child, packageName, depth + 1)) {
                return true
            }
        }
        return false
    }

    private fun getScreenSignature(node: AccessibilityNodeInfo?): String {
        val texts = mutableListOf<String>()
        collectAllText(node, texts)

        // Static keywords to exclude so they don't skew the signature matching
        val staticExclusions = setOf(
            "like", "comment", "share", "remix", "subscribe",
            "reels", "shorts", "spotlight", "for you", "following",
            "follow", "views", "likes", "comments", "add comment...", "reply"
        )

        val dynamicTexts = texts.filter {
            val clean = it.lowercase().trim()
            clean !in staticExclusions && clean.length > 2
        }

        return dynamicTexts.joinToString("|")
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            list.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllText(child, list)
        }
    }

    private fun signaturesMatch(sig1: String, sig2: String): Boolean {
        if (sig1 == sig2) return true
        if (sig1.isBlank() || sig2.isBlank()) return true

        val words1 = sig1.split("|").toSet()
        val words2 = sig2.split("|").toSet()

        val intersection = words1.intersect(words2)
        if (words1.isEmpty() || words2.isEmpty()) return true

        val overlapRatio = intersection.size.toFloat() / minOf(words1.size, words2.size).toFloat()
        // If there's more than 30% overlap of dynamic terms/usernames, consider it the same Reel
        return overlapRatio > 0.3f
    }

    override fun onInterrupt() {
        Log.d(TAG, "Zen Service Interrupted")
    }
}
