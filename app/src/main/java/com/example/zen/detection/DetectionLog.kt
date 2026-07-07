package com.example.zen.detection

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory ring buffer of detection decisions, readable by the debug screen (service and UI
 * share one process) and mirrored to logcat (`adb logcat -s ZenDetect`). This is what makes
 * on-device verification a five-minute job instead of guesswork.
 */
object DetectionLog {

    data class Entry(
        val at: Long,
        val pkg: String,
        val event: String,
        val matchedId: String? = null,
        val coverage: Float? = null,
        val state: String = "",
        val decision: String = ""
    )

    private const val TAG = "ZenDetect"
    private const val CAPACITY = 256

    private val buffer = ArrayDeque<Entry>(CAPACITY)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    @Synchronized
    fun log(entry: Entry) {
        if (buffer.size >= CAPACITY) buffer.removeFirst()
        buffer.addLast(entry)
        _entries.value = buffer.toList()
        Log.d(
            TAG,
            "${entry.event} pkg=${entry.pkg}" +
                (entry.matchedId?.let { " id=$it" } ?: "") +
                (entry.coverage?.let { " cov=%.2f".format(it) } ?: "") +
                (entry.state.takeIf { it.isNotEmpty() }?.let { " state=$it" } ?: "") +
                (entry.decision.takeIf { it.isNotEmpty() }?.let { " -> $it" } ?: "")
        )
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }
}
