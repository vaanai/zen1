package com.example.zen.detection

import android.content.Context
import android.util.Log
import com.example.zen.data.ZenPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads and refreshes the [DetectionConfig]. Singleton — the service and UI share one process,
 * so a successful remote refresh is visible to the running service immediately.
 *
 * Load order: validated cache file (a previously accepted remote config) → bundled asset.
 * Failure policy: totally silent. Any parse/network/schema problem keeps the current config;
 * a malformed remote can never degrade below the bundled fallback.
 */
object DetectionConfigStore {

    private const val TAG = "ZenDetect"
    private const val CACHE_FILE = "detection_config.json"
    private const val ASSET_FILE = "detection_config.json"

    /** Raw file in the project repo; updating it there hotfixes detection without an app update. */
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/vaanai/zen1/master/app/src/main/assets/detection_config.json"

    private const val FETCH_THROTTLE_MS = 6 * 60 * 60 * 1000L // 6h
    const val STALE_AFTER_MS = 7 * 24 * 60 * 60 * 1000L       // service refreshes if older

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    lateinit var current: DetectionConfig
        private set

    val isInitialized: Boolean get() = ::current.isInitialized

    sealed interface RefreshResult {
        data class Updated(val version: Int) : RefreshResult
        data object NotModified : RefreshResult
        data object Throttled : RefreshResult
        data class Failed(val reason: String) : RefreshResult
    }

    /** Idempotent. Safe to call from both MainActivity and the service. */
    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val app = context.applicationContext
        current = loadCache(app) ?: loadAsset(app)
    }

    suspend fun refreshFromRemote(context: Context, force: Boolean = false): RefreshResult =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            init(app)
            val prefs = ZenPrefs(app)
            val now = System.currentTimeMillis()
            if (!force && now - prefs.detectionConfigFetchedAt < FETCH_THROTTLE_MS) {
                return@withContext RefreshResult.Throttled
            }

            val result = try {
                val raw = fetch(REMOTE_URL)
                val parsed = json.decodeFromString<DetectionConfig>(raw)
                when {
                    !parsed.isValid() ->
                        RefreshResult.Failed("invalid schema (schemaVersion=${parsed.schemaVersion})")
                    parsed.configVersion <= current.configVersion ->
                        RefreshResult.NotModified
                    else -> {
                        File(app.filesDir, CACHE_FILE).writeText(raw)
                        current = parsed
                        prefs.detectionConfigVersion = parsed.configVersion
                        RefreshResult.Updated(parsed.configVersion)
                    }
                }
            } catch (e: Exception) {
                RefreshResult.Failed(e.javaClass.simpleName + ": " + (e.message ?: ""))
            }

            prefs.detectionConfigFetchedAt = now
            DetectionLog.log(
                DetectionLog.Entry(
                    at = now, pkg = "-", event = "configRefresh",
                    decision = result.toString()
                )
            )
            result
        }

    private fun loadCache(context: Context): DetectionConfig? = try {
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.exists()) null
        else json.decodeFromString<DetectionConfig>(file.readText()).takeIf { it.isValid() }
    } catch (e: Exception) {
        Log.w(TAG, "detection config cache unreadable, falling back to asset", e)
        null
    }

    private fun loadAsset(context: Context): DetectionConfig = try {
        val raw = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
        json.decodeFromString<DetectionConfig>(raw).also {
            check(it.isValid()) { "bundled detection config failed validation" }
        }
    } catch (e: Exception) {
        // The bundled asset is compile-time known-good; reaching this means a packaging bug.
        // An empty config fails open (no detection) rather than crashing the service.
        Log.e(TAG, "bundled detection config unreadable — detection disabled", e)
        DetectionConfig(schemaVersion = DetectionConfig.SUPPORTED_SCHEMA_VERSION, configVersion = 0)
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
