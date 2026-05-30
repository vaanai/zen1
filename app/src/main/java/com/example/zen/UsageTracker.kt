package com.example.zen

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.example.zen.data.AppUsageItem
import java.util.Calendar

class UsageTracker(private val context: Context) {

    private val targetApps = mapOf(
        "com.instagram.android" to Pair("Instagram", "#E1306C"),
        "com.google.android.youtube" to Pair("YouTube", "#FF0000"),
        "com.zhiliaoapp.musically" to Pair("TikTok", "#00F2FE"),
        "com.ss.android.ugc.trill" to Pair("TikTok", "#00F2FE"),
        "com.snapchat.android" to Pair("Snapchat", "#FFFC00")
    )

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.noteOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getUsageStats(): String {
        if (!isUsageAccessGranted()) {
            return "Permissions Required: Please enable Usage Access to track time saved."
        }

        val items = getDetailedUsageStats()
        val totalMinutes = items.sumOf { it.timeSpentMinutes }
        return "Time spent in addictive apps today: $totalMinutes minutes."
    }

    fun getDetailedUsageStats(): List<AppUsageItem> {
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
        ) ?: return emptyList()

        // Accumulate statistics as queryUsageStats can return multiple stats per package
        val accumulatedStats = mutableMapOf<String, Long>()
        for (stats in usageStatsList) {
            val pkg = stats.packageName
            if (targetApps.containsKey(pkg)) {
                val current = accumulatedStats.getOrDefault(pkg, 0L)
                accumulatedStats[pkg] = current + stats.totalTimeInForeground
            }
        }

        return targetApps.entries
            .filter { accumulatedStats.containsKey(it.key) }
            .map { (pkg, info) ->
                val (appName, color) = info
                val millis = accumulatedStats[pkg] ?: 0L
                val minutes = millis / 1000 / 60
                AppUsageItem(
                    packageName = pkg,
                    appName = appName,
                    timeSpentMinutes = minutes,
                    colorHex = color
                )
            }
            .distinctBy { it.appName } // Combine duplicates like musically and trill
    }
}
