package com.kitewatch.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.settings.BackupRestoreRoute
import com.kitewatch.feature.settings.CsvImportRoute
import com.kitewatch.feature.settings.SettingsRoute

const val SETTINGS_ROUTE = "settings"
const val BACKUP_RESTORE_ROUTE = "settings/backup_restore"
const val CSV_IMPORT_ROUTE = "settings/csv_import"

// private const val ABOUT_ROUTE = "settings/about"
// private const val GUIDEBOOK_ROUTE = "settings/guidebook"
// private const val PRIVACY_ROUTE = "settings/privacy"

fun NavGraphBuilder.settingsScreen(
    onNavigateToAbout: () -> Unit = {},
    onNavigateToGuidebook: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToCsvImport: () -> Unit = {},
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsRoute(
            onNavigateToAbout = onNavigateToAbout,
            onNavigateToGuidebook = onNavigateToGuidebook,
            onNavigateToPrivacy = onNavigateToPrivacy,
            onNavigateToBackupRestore = onNavigateToBackupRestore,
            onNavigateToCsvImport = onNavigateToCsvImport,
        )
    }
}

fun NavGraphBuilder.backupRestoreScreen(onNavigateUp: () -> Unit) {
    composable(route = BACKUP_RESTORE_ROUTE) {
        BackupRestoreRoute(onNavigateUp = onNavigateUp)
    }
}

fun NavGraphBuilder.csvImportScreen(onNavigateUp: () -> Unit) {
    composable(route = CSV_IMPORT_ROUTE) {
        CsvImportRoute(onNavigateUp = onNavigateUp)
    }
}
