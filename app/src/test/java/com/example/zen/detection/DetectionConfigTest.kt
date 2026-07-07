package com.example.zen.detection

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Mirrors app/src/main/assets/detection_config.json — keep the shape in sync.
    private val sample = """
        {
          "schemaVersion": 1,
          "configVersion": 3,
          "updatedAt": "2026-07-07",
          "apps": [
            {
              "key": "instagram",
              "packages": ["com.instagram.android"],
              "strategy": "VIEW_ID",
              "viewIds": ["clips_viewer_view_pager"],
              "minCoverage": 0.55,
              "requireVisible": true,
              "classNameHints": [],
              "negativeViewIds": []
            },
            {
              "key": "tiktok",
              "packages": ["com.zhiliaoapp.musically", "com.ss.android.ugc.trill"],
              "strategy": "WHOLE_APP"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses valid config`() {
        val config = json.decodeFromString<DetectionConfig>(sample)
        assertTrue(config.isValid())
        assertEquals(3, config.configVersion)
        assertEquals(MatchStrategy.VIEW_ID, config.matcherFor("com.instagram.android")?.strategy)
        assertEquals(MatchStrategy.WHOLE_APP, config.matcherFor("com.ss.android.ugc.trill")?.strategy)
        assertNull(config.matcherFor("com.example.unknown"))
    }

    @Test
    fun `matcher defaults are sensible`() {
        val config = json.decodeFromString<DetectionConfig>(sample)
        val tiktok = config.matcherFor("com.zhiliaoapp.musically")!!
        assertTrue(tiktok.viewIds.isEmpty())
        assertTrue(tiktok.requireVisible)
        assertEquals(0.55f, tiktok.minCoverage)
    }

    @Test
    fun `view id sets are lowercased for exact matching`() {
        val config = json.decodeFromString<DetectionConfig>(
            sample.replace("clips_viewer_view_pager", "Clips_Viewer_View_Pager")
        )
        val ig = config.matcherFor("com.instagram.android")!!
        assertTrue("clips_viewer_view_pager" in ig.viewIdsLower)
    }

    @Test
    fun `unknown json keys are ignored for forward compatibility`() {
        val withExtra = sample.replace(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1, \"futureField\": {\"nested\": true},"
        )
        assertTrue(json.decodeFromString<DetectionConfig>(withExtra).isValid())
    }

    @Test
    fun `wrong schema version fails validation`() {
        val wrong = sample.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        assertFalse(json.decodeFromString<DetectionConfig>(wrong).isValid())
    }

    @Test
    fun `empty apps fails validation`() {
        val empty = """{"schemaVersion": 1, "configVersion": 1, "apps": []}"""
        assertFalse(json.decodeFromString<DetectionConfig>(empty).isValid())
    }
}
