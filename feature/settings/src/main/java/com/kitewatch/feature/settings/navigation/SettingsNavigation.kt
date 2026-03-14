package com.kitewatch.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.settings.SettingsRoute

const val SETTINGS_ROUTE = "settings"

// private const val ABOUT_ROUTE = "settings/about"
// private const val GUIDEBOOK_ROUTE = "settings/guidebook"
// private const val PRIVACY_ROUTE = "settings/privacy"

fun NavGraphBuilder.settingsScreen(
    onNavigateToAbout: () -> Unit = {},
    onNavigateToGuidebook: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsRoute(
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToGuidebook = onNavigateToGuidebook,
            onNavigateToPrivacy = onNavigateToPrivacy,
        )
    }
}
