package com.kitewatch.feature.onboarding

enum class OnboardingStep {
    TERMS,
    BIOMETRIC_SETUP,
    ZERODHA_LOGIN,
    ACCOUNT_CONFIRMATION,
    COMPLETE,
}

sealed interface OnboardingIntent {
    data object AcceptTerms : OnboardingIntent

    data object SetupBiometric : OnboardingIntent

    data object SkipBiometricToPin : OnboardingIntent

    data object LaunchKiteLogin : OnboardingIntent

    data class OnLoginSuccess(
        val requestToken: String,
    ) : OnboardingIntent

    data class OnLoginFailure(
        val error: String,
    ) : OnboardingIntent

    data object ConfirmAndStart : OnboardingIntent
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.TERMS,
    val termsChecked: Boolean = false,
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val boundUserName: String = "",
    val boundUserId: String = "",
    val biometricUnavailable: Boolean = false,
)

sealed interface OnboardingSideEffect {
    data class LaunchKiteAuth(
        val apiKey: String,
    ) : OnboardingSideEffect

    data object NavigateToMain : OnboardingSideEffect

    data class ShowError(
        val message: String,
    ) : OnboardingSideEffect
}
