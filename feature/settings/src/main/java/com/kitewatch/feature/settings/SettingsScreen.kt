package com.kitewatch.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.feature.settings.component.FundBalanceEntrySheet
import com.kitewatch.feature.settings.component.SettingsRow
import com.kitewatch.feature.settings.component.SettingsSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToGuidebook: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is SettingsSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    SettingsScreen(
        state = state,
        onIntent = viewModel::processIntent,
        snackbarHostState = snackbarHostState,
        navigation =
            SettingsNavigationActions(
                onNavigateToAbout = onNavigateToAbout,
                onNavigateToGuidebook = onNavigateToGuidebook,
                onNavigateToPrivacy = onNavigateToPrivacy,
            ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    navigation: SettingsNavigationActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    if (state.showFundEntrySheet) {
        FundBalanceEntrySheet(
            onIntent = onIntent,
            errorMessage = state.fundEntryError,
            isSaving = state.isSavingFundEntry,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // ── Fund Balance ──────────────────────────────────────────────────────
            item { SettingsSectionHeader(title = "Fund Balance") }
            item {
                SettingsRow(
                    label = "Current Balance",
                    subtitle = state.fundBalance,
                    trailing = {
                        TextButton(onClick = { onIntent(SettingsIntent.ShowFundEntrySheet) }) {
                            Text("Add Entry")
                        }
                    },
                )
            }
            item { HorizontalDivider() }

            // ── Appearance ────────────────────────────────────────────────────────
            item { SettingsSectionHeader(title = "Appearance") }
            item {
                SettingsRow(
                    label = "Dark Theme",
                    trailing = {
                        Switch(
                            checked = state.isDarkTheme,
                            onCheckedChange = { onIntent(SettingsIntent.ToggleTheme) },
                        )
                    },
                )
            }
            item { HorizontalDivider() }

            // ── Sync ──────────────────────────────────────────────────────────────
            item { SettingsSectionHeader(title = "Sync") }
            item {
                SettingsRow(
                    label = "Sync Schedule",
                    subtitle = "Automatic (every 15 min while market is open)",
                )
            }
            item { HorizontalDivider() }

            // ── About ─────────────────────────────────────────────────────────────
            item { SettingsSectionHeader(title = "About") }
            item {
                SettingsRow(
                    label = "Zerodha Account",
                    subtitle = state.zerodhaUserId,
                )
            }
            item {
                SettingsRow(
                    label = "App Version",
                    subtitle = state.appVersion,
                )
            }
            item {
                SettingsRow(
                    label = "About KiteWatch",
                    onClick = navigation.onNavigateToAbout,
                )
            }
            item {
                SettingsRow(
                    label = "Guidebook",
                    onClick = navigation.onNavigateToGuidebook,
                )
            }
            item {
                SettingsRow(
                    label = "Privacy Policy",
                    onClick = navigation.onNavigateToPrivacy,
                )
            }
        }
    }
}
