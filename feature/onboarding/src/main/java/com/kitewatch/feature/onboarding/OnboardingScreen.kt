package com.kitewatch.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitewatch.feature.onboarding.component.AccountConfirmationStep
import com.kitewatch.feature.onboarding.component.BiometricSetupCallbacks
import com.kitewatch.feature.onboarding.component.BiometricSetupStep
import com.kitewatch.feature.onboarding.component.TermsAcceptanceStep
import com.kitewatch.feature.onboarding.component.ZerodhaLoginStep
import com.kitewatch.infra.auth.BiometricAuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingRoute(
    biometricAuthManager: BiometricAuthManager,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context as? FragmentActivity }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is OnboardingSideEffect.LaunchKiteAuth -> {
                    activity?.let { KiteAuthLauncher.launch(it, effect.apiKey) }
                }
                is OnboardingSideEffect.NavigateToMain -> onOnboardingComplete()
                is OnboardingSideEffect.ShowError -> { /* handled via state.loginError */ }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    LinearProgressIndicator(
                        progress = { state.step.progressFraction() },
                    )
                },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                slideInHorizontally { fullWidth -> fullWidth } togetherWith
                    slideOutHorizontally { fullWidth -> -fullWidth }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            label = "onboarding_step",
        ) { step ->
            when (step) {
                OnboardingStep.TERMS -> {
                    TermsAcceptanceStep(
                        termsChecked = state.termsChecked,
                        onCheckedChange = { viewModel.processIntent(OnboardingIntent.AcceptTerms) },
                        onContinue = { viewModel.onTermsContinue() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                OnboardingStep.BIOMETRIC_SETUP -> {
                    BiometricSetupStep(
                        biometricUnavailable = state.biometricUnavailable,
                        biometricAuthManager = biometricAuthManager,
                        activity = activity ?: return@AnimatedContent,
                        callbacks =
                            BiometricSetupCallbacks(
                                onBiometricSuccess = {
                                    viewModel.processIntent(OnboardingIntent.SetupBiometric)
                                },
                                onBiometricUnavailable = { viewModel.onBiometricUnavailable() },
                                onPinSetup = {
                                    viewModel.processIntent(OnboardingIntent.SkipBiometricToPin)
                                },
                            ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                OnboardingStep.ZERODHA_LOGIN -> {
                    ZerodhaLoginStep(
                        isLoading = state.isLoading,
                        loginError = state.loginError,
                        onConnectClick = {
                            viewModel.processIntent(OnboardingIntent.LaunchKiteLogin)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                OnboardingStep.ACCOUNT_CONFIRMATION -> {
                    AccountConfirmationStep(
                        userName = state.boundUserName,
                        userId = state.boundUserId,
                        onStartClick = {
                            viewModel.processIntent(OnboardingIntent.ConfirmAndStart)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                OnboardingStep.COMPLETE -> {
                    // Transition handled by side effect — nothing to render
                }
            }
        }
    }
}

private fun OnboardingStep.progressFraction(): Float =
    when (this) {
        OnboardingStep.TERMS -> 0.25f
        OnboardingStep.BIOMETRIC_SETUP -> 0.50f
        OnboardingStep.ZERODHA_LOGIN -> 0.75f
        OnboardingStep.ACCOUNT_CONFIRMATION -> 1.00f
        OnboardingStep.COMPLETE -> 1.00f
    }
