package com.example.zen.ui.main

import com.example.zen.data.AppUsageItem
import com.example.zen.data.ZenStatusProvider
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoadsCorrectState() = runTest {
    val fakeProvider = FakeZenStatusProvider(
        accessibilityEnabled = true,
        usageAccessEnabled = true,
        stats = listOf(AppUsageItem("com.instagram.android", "Instagram", 15L, "#E1306C"))
    )
    val viewModel = MainScreenViewModel(fakeProvider)
    viewModel.refreshState()
    val state = viewModel.uiState.value
    assertEquals(true, state.isAccessibilityEnabled)
    assertEquals(true, state.isUsageAccessEnabled)
    assertEquals(15L, state.totalTimeSpentMinutes)
    assertEquals(1, state.appStatsList.size)
    assertEquals("Instagram", state.appStatsList[0].appName)
  }
}

private class FakeZenStatusProvider(
    private val accessibilityEnabled: Boolean,
    private val usageAccessEnabled: Boolean,
    private val stats: List<AppUsageItem>
) : ZenStatusProvider {
    override fun isAccessibilityEnabled(): Boolean = accessibilityEnabled
    override fun isUsageAccessEnabled(): Boolean = usageAccessEnabled
    override fun getDetailedUsageStats(): List<AppUsageItem> = stats
}
