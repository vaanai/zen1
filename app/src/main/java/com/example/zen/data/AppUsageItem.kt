package com.example.zen.data

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val timeSpentMinutes: Long,
    val colorHex: String
)
