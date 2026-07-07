package com.example.zen.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the Json configuration in ZenPrefs. These tests exist because the appConfigs getter
 * resets to defaults on any decode exception — a parsing regression here would silently wipe
 * every per-app guard setting.
 */
class AppGuardConfigTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `legacy stored json without new fields decodes with defaults`() {
        val legacy = """{"instagram":{"enabled":true,"mode":"FRICTION","scrollAllowance":2}}"""
        val map = json.decodeFromString<Map<String, AppGuardConfig>>(legacy)
        val ig = map.getValue("instagram")
        assertEquals(2, ig.scrollAllowance)
        assertEquals(ExitAction.LEAVE_APP, ig.exitAction)
        assertTrue(ig.friendPass)
    }

    @Test
    fun `unknown enum value coerces to default instead of throwing`() {
        val future = """{"instagram":{"enabled":true,"mode":"FRICTION","scrollAllowance":1,"exitAction":"TELEPORT"}}"""
        val map = json.decodeFromString<Map<String, AppGuardConfig>>(future)
        assertEquals(ExitAction.LEAVE_APP, map.getValue("instagram").exitAction)
        assertEquals(1, map.getValue("instagram").scrollAllowance) // rest of the config survives
    }

    @Test
    fun `round trip preserves all fields`() {
        val original = mapOf(
            "instagram" to AppGuardConfig(
                enabled = false,
                scrollAllowance = 4,
                exitAction = ExitAction.MESSAGES,
                friendPass = false
            )
        )
        val decoded = json.decodeFromString<Map<String, AppGuardConfig>>(json.encodeToString(original))
        assertEquals(original, decoded)
    }
}
