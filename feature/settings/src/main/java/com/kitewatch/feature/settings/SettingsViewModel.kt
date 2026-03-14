package com.kitewatch.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.data.preferences.ThemePreferenceRepository
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.usecase.fund.AddFundEntryUseCase
import com.kitewatch.domain.usecase.fund.GetFundBalanceUseCase
import com.kitewatch.ui.formatter.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val themePreferenceRepository: ThemePreferenceRepository,
        private val addFundEntryUseCase: AddFundEntryUseCase,
        private val getFundBalanceUseCase: GetFundBalanceUseCase,
        private val accountBindingRepository: AccountBindingRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsState())
        val state: StateFlow<SettingsState> = _state.asStateFlow()

        private val _sideEffect = Channel<SettingsSideEffect>(Channel.BUFFERED)
        val sideEffect: Flow<SettingsSideEffect> = _sideEffect.receiveAsFlow()

        init {
            _state.update { it.copy(appVersion = appVersion()) }

            combine(
                themePreferenceRepository.isDarkTheme,
                getFundBalanceUseCase.execute(),
            ) { isDark, balance ->
                _state.update {
                    it.copy(
                        isDarkTheme = isDark,
                        fundBalance = CurrencyFormatter.format(balance),
                    )
                }
            }.catch { /* non-fatal */ }.launchIn(viewModelScope)

            viewModelScope.launch {
                val binding = accountBindingRepository.getBinding()
                if (binding != null) {
                    _state.update { it.copy(zerodhaUserId = maskUserId(binding.userId)) }
                }
            }
        }

        fun processIntent(intent: SettingsIntent) {
            when (intent) {
                is SettingsIntent.ToggleTheme -> toggleTheme()
                is SettingsIntent.AddFundEntry -> addFundEntry(intent)
                is SettingsIntent.ShowFundEntrySheet ->
                    _state.update {
                        it.copy(
                            showFundEntrySheet = true,
                            fundEntryError = null,
                        )
                    }
                is SettingsIntent.DismissFundEntrySheet ->
                    _state.update {
                        it.copy(
                            showFundEntrySheet = false,
                            fundEntryError = null,
                        )
                    }
            }
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private fun toggleTheme() {
            viewModelScope.launch {
                themePreferenceRepository.setDarkTheme(!_state.value.isDarkTheme)
            }
        }

        private fun addFundEntry(intent: SettingsIntent.AddFundEntry) {
            viewModelScope.launch {
                _state.update { it.copy(isSavingFundEntry = true, fundEntryError = null) }
                addFundEntryUseCase
                    .execute(
                        amount = intent.amount,
                        date = intent.date,
                        note = intent.note,
                        entryType = intent.entryType,
                    ).onSuccess {
                        _state.update { it.copy(showFundEntrySheet = false, isSavingFundEntry = false) }
                        _sideEffect.send(SettingsSideEffect.ShowSnackbar("Fund entry saved"))
                    }.onFailure { t ->
                        _state.update {
                            it.copy(
                                isSavingFundEntry = false,
                                fundEntryError = t.message ?: "Invalid amount",
                            )
                        }
                    }
            }
        }

        private fun maskUserId(userId: String): String {
            if (userId.length <= 4) return "****"
            return "${userId.take(2)}${"*".repeat(userId.length - 4)}${userId.takeLast(2)}"
        }

        private fun appVersion(): String =
            runCatching {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: ""
            }.getOrDefault("")
    }
