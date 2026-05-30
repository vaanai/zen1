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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // We only care about our target apps
        if (!targetPackages.contains(packageName)) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleScrollEvent(packageName, event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Here we could implement hard blocking, but as per the "Friend Pass" requirement,
                // we allow them to view the content they landed on, but block the *scroll*.
            }
        }
    }

    private fun handleScrollEvent(packageName: String, event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        // If we are in TikTok, it's exclusively short-form, so any scroll is blocked.
        if (packageName.contains("zhiliaoapp") || packageName.contains("ugc.trill")) {
            blockScroll("TikTok")
            return
        }

        // For others, check if the current screen contains indicators of short-form content
        if (isShortFormContent(rootNode)) {
            blockScroll(packageName)
        }
    }

    private fun blockScroll(appName: String) {
        Log.d(TAG, "Blocked doom-scroll in $appName")
        // Perform a global back action to exit the Reels/Shorts view and return to home/messaging
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun isShortFormContent(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        val text = node.text?.toString()?.lowercase()
        val desc = node.contentDescription?.toString()?.lowercase()

        // Keywords that heavily indicate we are in the addictive short-form feed
        val indicators = listOf("reels", "shorts", "spotlight", "for you")
        
        if (text != null && indicators.any { text.contains(it) }) return true
        if (desc != null && indicators.any { desc.contains(it) }) return true

        for (i in 0 until node.childCount) {
            if (isShortFormContent(node.getChild(i))) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "Zen Service Interrupted")
    }
}
