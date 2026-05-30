package com.example.zen

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

class UsageTracker(private val context: Context) {
    fun getUsageStats(): String {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return "Permissions Required: Please enable Usage Access to track time saved."
        }

        var totalTimeBlockedApps = 0L
        val blockedApps = listOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.snapchat.android"
        )

        for (stats in usageStatsList) {
            if (blockedApps.contains(stats.packageName)) {
                totalTimeBlockedApps += stats.totalTimeInForeground
            }
        }

        val minutes = totalTimeBlockedApps / 1000 / 60
        return "Time spent in addictive apps today: $minutes minutes."
    }
}
