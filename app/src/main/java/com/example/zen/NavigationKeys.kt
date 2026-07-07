package com.example.zen

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Onboarding : NavKey

@Serializable data object Dashboard : NavKey

@Serializable data object SettingsRoute : NavKey

@Serializable data object DebugRoute : NavKey
