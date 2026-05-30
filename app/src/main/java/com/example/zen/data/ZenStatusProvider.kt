package com.example.zen.data

import android.content.Context
import android.provider.Settings
import com.example.zen.UsageTracker
import com.example.zen.ZenAccessibilityService

interface ZenStatusProvider {
    fun isAccessibilityEnabled(): Boolean
    fun isUsageAccessEnabled(): Boolean
    fun getDetailedUsageStats(): List<AppUsageItem>
}

class DefaultZenStatusProvider(private val context: Context) : ZenStatusProvider {
    private val usageTracker = UsageTracker(context)

    override fun isAccessibilityEnabled(): Boolean {
        val service = "${context.packageName}/${ZenAccessibilityService::class.java.name}"
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        colonSplitter.setString(settingValue)
        while (colonSplitter.hasNext()) {
            val accessibilityService = colonSplitter.next()
            if (accessibilityService.equals(service, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    override fun isUsageAccessEnabled(): Boolean {
        return usageTracker.isUsageAccessGranted()
    }

    override fun getDetailedUsageStats(): List<AppUsageItem> {
        return usageTracker.getDetailedUsageStats()
    }
}
