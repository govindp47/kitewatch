package com.kitewatch.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kitewatch.feature.onboarding.OnboardingRoute
import com.kitewatch.infra.auth.BiometricAuthManager

const val ONBOARDING_ROUTE = "onboarding"

fun NavGraphBuilder.onboardingScreen(
    biometricAuthManager: BiometricAuthManager,
    onOnboardingComplete: () -> Unit,
) {
    composable(route = ONBOARDING_ROUTE) {
        OnboardingRoute(
            biometricAuthManager = biometricAuthManager,
            onOnboardingComplete = onOnboardingComplete,
        )
    }
}
