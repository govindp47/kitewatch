package com.kitewatch.feature.settings

data class SettingsNavigationActions(
    val onNavigateToAbout: () -> Unit = {},
    val onNavigateToGuidebook: () -> Unit = {},
    val onNavigateToPrivacy: () -> Unit = {},
    val onNavigateToBackupRestore: () -> Unit = {},
)
