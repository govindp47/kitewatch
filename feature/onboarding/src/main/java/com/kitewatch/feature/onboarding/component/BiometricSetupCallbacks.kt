package com.kitewatch.feature.onboarding.component

data class BiometricSetupCallbacks(
    val onBiometricSuccess: () -> Unit,
    val onBiometricUnavailable: () -> Unit,
    val onPinSetup: () -> Unit,
)
