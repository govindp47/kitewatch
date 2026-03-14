package com.kitewatch.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.repository.TokenStore
import com.kitewatch.domain.usecase.auth.BindAccountUseCase
import com.kitewatch.feature.onboarding.preferences.OnboardingPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val bindAccountUseCase: BindAccountUseCase,
        @Suppress("UnusedPrivateMember")
        private val accountBindingRepository: AccountBindingRepository,
        private val tokenStore: TokenStore,
        private val onboardingPreferenceRepository: OnboardingPreferenceRepository,
        @Named("kite_api_secret") private val apiSecret: String,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OnboardingState())
        val state: StateFlow<OnboardingState> = _state.asStateFlow()

        private val _sideEffect = Channel<OnboardingSideEffect>(Channel.BUFFERED)
        val sideEffect: Flow<OnboardingSideEffect> = _sideEffect.receiveAsFlow()

        // Resolved from TokenStore (stored during previous API key entry or BuildConfig default)
        private val apiKey: String get() = tokenStore.getApiKey() ?: ""

        fun processIntent(intent: OnboardingIntent) {
            when (intent) {
                is OnboardingIntent.AcceptTerms -> {
                    _state.update { it.copy(termsChecked = !it.termsChecked) }
                }
                is OnboardingIntent.SetupBiometric -> advanceFromBiometric()
                is OnboardingIntent.SkipBiometricToPin -> advanceFromBiometric()
                is OnboardingIntent.LaunchKiteLogin -> launchKiteLogin()
                is OnboardingIntent.OnLoginSuccess -> handleLoginSuccess(intent.requestToken)
                is OnboardingIntent.OnLoginFailure -> {
                    _state.update { it.copy(isLoading = false, loginError = intent.error) }
                }
                is OnboardingIntent.ConfirmAndStart -> confirmAndStart()
            }
        }

        fun onTermsContinue() {
            if (_state.value.termsChecked) {
                _state.update { it.copy(step = OnboardingStep.BIOMETRIC_SETUP) }
            }
        }

        fun onBiometricUnavailable() {
            _state.update { it.copy(biometricUnavailable = true) }
        }

        // ── Private helpers ───────────────────────────────────────────────────

        private fun advanceFromBiometric() {
            _state.update { it.copy(step = OnboardingStep.ZERODHA_LOGIN) }
        }

        private fun launchKiteLogin() {
            _state.update { it.copy(isLoading = true, loginError = null) }
            viewModelScope.launch {
                _sideEffect.send(OnboardingSideEffect.LaunchKiteAuth(apiKey))
            }
        }

        private fun handleLoginSuccess(requestToken: String) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, loginError = null) }
                bindAccountUseCase
                    .execute(
                        apiKey = apiKey,
                        requestToken = requestToken,
                        apiSecret = apiSecret,
                    ).onSuccess { binding ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                step = OnboardingStep.ACCOUNT_CONFIRMATION,
                                boundUserName = binding.userName,
                                boundUserId = binding.userId,
                            )
                        }
                    }.onFailure { t ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                loginError = t.message ?: "Authentication failed",
                            )
                        }
                    }
            }
        }

        private fun confirmAndStart() {
            viewModelScope.launch {
                onboardingPreferenceRepository.setOnboardingComplete(true)
                _state.update { it.copy(step = OnboardingStep.COMPLETE) }
                _sideEffect.send(OnboardingSideEffect.NavigateToMain)
            }
        }
    }
