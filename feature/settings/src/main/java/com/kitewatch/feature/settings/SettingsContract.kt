package com.kitewatch.feature.settings

import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import java.time.LocalDate

sealed interface SettingsIntent {
    data object ToggleTheme : SettingsIntent

    data class AddFundEntry(
        val amount: Paisa,
        val date: LocalDate,
        val note: String?,
        val entryType: FundEntryType,
    ) : SettingsIntent

    data object ShowFundEntrySheet : SettingsIntent

    data object DismissFundEntrySheet : SettingsIntent
}

data class SettingsState(
    val isDarkTheme: Boolean = false,
    val fundBalance: String = "₹0.00",
    val zerodhaUserId: String = "—",
    val appVersion: String = "",
    val showFundEntrySheet: Boolean = false,
    val fundEntryError: String? = null,
    val isSavingFundEntry: Boolean = false,
)

sealed interface SettingsSideEffect {
    data class ShowSnackbar(
        val message: String,
    ) : SettingsSideEffect
}
