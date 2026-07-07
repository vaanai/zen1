package com.example.zen.data

import android.content.Context
import android.os.Build

/**
 * Human-readable build identity, e.g. "v1.2.137 (237)".
 *
 * Read from PackageManager rather than BuildConfig because `buildConfig = false`
 * in the module — and this works identically for any variant.
 */
object AppVersion {
    fun describe(context: Context): String {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION") pi.versionCode.toLong()
        }
        return "v${pi.versionName} ($code)"
    }
}
