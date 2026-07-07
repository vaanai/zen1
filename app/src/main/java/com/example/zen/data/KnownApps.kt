package com.example.zen.data

/**
 * A user-facing app that can be guarded. Some apps ship under more than one package.
 * [key] is the stable identifier shared with the detection config and per-app guard settings.
 */
data class GuardedApp(
    val key: String,
    val name: String,
    val colorHex: String,
    val packages: List<String>
)

/** Central catalog of the short-form-capable apps Zen knows how to guard. */
object KnownApps {
    val apps: List<GuardedApp> = listOf(
        GuardedApp("instagram", "Instagram", "#E1306C", listOf("com.instagram.android")),
        GuardedApp("youtube", "YouTube", "#FF0000", listOf("com.google.android.youtube")),
        GuardedApp("tiktok", "TikTok", "#00F2FE", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
        GuardedApp("snapchat", "Snapchat", "#FFFC00", listOf("com.snapchat.android"))
    )

    val allPackages: Set<String> = apps.flatMap { it.packages }.toSet()

    private val byPackage: Map<String, GuardedApp> =
        apps.flatMap { app -> app.packages.map { it to app } }.toMap()

    fun nameFor(pkg: String): String? = byPackage[pkg]?.name
    fun colorFor(pkg: String): String? = byPackage[pkg]?.colorHex
    fun keyFor(pkg: String): String? = byPackage[pkg]?.key
    fun byKey(key: String): GuardedApp? = apps.firstOrNull { it.key == key }
}
