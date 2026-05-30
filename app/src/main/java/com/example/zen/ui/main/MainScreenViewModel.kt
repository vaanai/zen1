package com.example.zen.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zen.data.AppUsageItem
import com.example.zen.data.ZenStatusProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isAccessibilityEnabled: Boolean = false,
    val isUsageAccessEnabled: Boolean = false,
    val totalTimeSpentMinutes: Long = 0L,
    val appStatsList: List<AppUsageItem> = emptyList()
)

class MainScreenViewModel(
    private val statusProvider: ZenStatusProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        startTracking()
    }

    fun refreshState() {
        val hasUsage = statusProvider.isUsageAccessEnabled()
        val hasAccessibility = statusProvider.isAccessibilityEnabled()
        val stats = if (hasUsage) statusProvider.getDetailedUsageStats() else emptyList()
        val totalMins = stats.sumOf { it.timeSpentMinutes }

        _uiState.update {
            it.copy(
                isAccessibilityEnabled = hasAccessibility,
                isUsageAccessEnabled = hasUsage,
                totalTimeSpentMinutes = totalMins,
                appStatsList = stats
            )
        }
    }

    private fun startTracking() {
        viewModelScope.launch {
            while (true) {
                refreshState()
                delay(3000) // update every 3 seconds for active UI updates
            }
        }
    }
}
