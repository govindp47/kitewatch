package com.kitewatch.feature.onboarding

import com.kitewatch.domain.model.AccountBinding
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.repository.TokenStore
import com.kitewatch.domain.usecase.auth.BindAccountUseCase
import com.kitewatch.feature.onboarding.preferences.OnboardingPreferenceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val bindAccountUseCase: BindAccountUseCase = mockk()
    private val accountBindingRepository: AccountBindingRepository = mockk(relaxed = true)
    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val onboardingPreferenceRepository: OnboardingPreferenceRepository = mockk(relaxed = true)

    private val testApiKey = "test_api_key"
    private val testApiSecret = "test_api_secret"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { tokenStore.getApiKey() } returns testApiKey
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        OnboardingViewModel(
            bindAccountUseCase = bindAccountUseCase,
            accountBindingRepository = accountBindingRepository,
            tokenStore = tokenStore,
            onboardingPreferenceRepository = onboardingPreferenceRepository,
            apiSecret = testApiSecret,
        )

    // ── Terms step ────────────────────────────────────────────────────────────

    @Test
    fun `initial step is TERMS`() {
        val vm = buildViewModel()
        assertEquals(OnboardingStep.TERMS, vm.state.value.step)
    }

    @Test
    fun `AcceptTerms intent toggles termsChecked`() {
        val vm = buildViewModel()

        assertFalse(vm.state.value.termsChecked)
        vm.processIntent(OnboardingIntent.AcceptTerms)
        assertTrue(vm.state.value.termsChecked)
        vm.processIntent(OnboardingIntent.AcceptTerms)
        assertFalse(vm.state.value.termsChecked)
    }

    @Test
    fun `onTermsContinue advances to BIOMETRIC_SETUP only when checked`() {
        val vm = buildViewModel()

        // Not checked — should not advance
        vm.onTermsContinue()
        assertEquals(OnboardingStep.TERMS, vm.state.value.step)

        // Check and retry — should advance
        vm.processIntent(OnboardingIntent.AcceptTerms)
        vm.onTermsContinue()
        assertEquals(OnboardingStep.BIOMETRIC_SETUP, vm.state.value.step)
    }

    // ── Biometric step ────────────────────────────────────────────────────────

    @Test
    fun `SetupBiometric intent advances to ZERODHA_LOGIN`() {
        val vm = buildViewModel()
        vm.processIntent(OnboardingIntent.AcceptTerms)
        vm.onTermsContinue()

        vm.processIntent(OnboardingIntent.SetupBiometric)
        assertEquals(OnboardingStep.ZERODHA_LOGIN, vm.state.value.step)
    }

    @Test
    fun `SkipBiometricToPin intent advances to ZERODHA_LOGIN`() {
        val vm = buildViewModel()
        vm.processIntent(OnboardingIntent.AcceptTerms)
        vm.onTermsContinue()

        vm.processIntent(OnboardingIntent.SkipBiometricToPin)
        assertEquals(OnboardingStep.ZERODHA_LOGIN, vm.state.value.step)
    }

    @Test
    fun `onBiometricUnavailable sets biometricUnavailable flag`() {
        val vm = buildViewModel()

        assertFalse(vm.state.value.biometricUnavailable)
        vm.onBiometricUnavailable()
        assertTrue(vm.state.value.biometricUnavailable)
    }

    // ── Login step ────────────────────────────────────────────────────────────

    @Test
    fun `OnLoginSuccess with valid token advances to ACCOUNT_CONFIRMATION`() =
        runTest {
            val binding =
                AccountBinding(
                    userId = "AB1234",
                    userName = "Test User",
                    apiKey = testApiKey,
                    boundAt = Instant.EPOCH,
                )
            coEvery {
                bindAccountUseCase.execute(testApiKey, "valid_token", testApiSecret)
            } returns Result.success(binding)

            val vm = buildViewModel()
            vm.processIntent(OnboardingIntent.OnLoginSuccess("valid_token"))
            advanceUntilIdle()

            assertEquals(OnboardingStep.ACCOUNT_CONFIRMATION, vm.state.value.step)
            assertEquals("Test User", vm.state.value.boundUserName)
            assertEquals("AB1234", vm.state.value.boundUserId)
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun `OnLoginSuccess with failed bind sets loginError`() =
        runTest {
            coEvery {
                bindAccountUseCase.execute(testApiKey, "bad_token", testApiSecret)
            } returns Result.failure(Exception("Invalid checksum"))

            val vm = buildViewModel()
            vm.processIntent(OnboardingIntent.OnLoginSuccess("bad_token"))
            advanceUntilIdle()

            assertNotNull(vm.state.value.loginError)
            assertEquals("Invalid checksum", vm.state.value.loginError)
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun `OnLoginFailure sets error and clears loading`() {
        val vm = buildViewModel()
        vm.processIntent(OnboardingIntent.OnLoginFailure("Session expired"))

        assertEquals("Session expired", vm.state.value.loginError)
        assertFalse(vm.state.value.isLoading)
    }

    // ── Confirmation step ─────────────────────────────────────────────────────

    @Test
    fun `ConfirmAndStart persists onboarding completion and emits NavigateToMain`() =
        runTest {
            val vm = buildViewModel()
            vm.processIntent(OnboardingIntent.ConfirmAndStart)
            advanceUntilIdle()

            assertEquals(OnboardingStep.COMPLETE, vm.state.value.step)
            coVerify { onboardingPreferenceRepository.setOnboardingComplete(true) }
        }

    // ── Step order ────────────────────────────────────────────────────────────

    @Test
    fun `full happy path traverses all steps in order`() =
        runTest {
            val binding =
                AccountBinding(
                    userId = "AB1234",
                    userName = "Test User",
                    apiKey = testApiKey,
                    boundAt = Instant.EPOCH,
                )
            coEvery {
                bindAccountUseCase.execute(any(), any(), any())
            } returns Result.success(binding)

            val vm = buildViewModel()

            // TERMS
            assertEquals(OnboardingStep.TERMS, vm.state.value.step)
            vm.processIntent(OnboardingIntent.AcceptTerms)
            vm.onTermsContinue()

            // BIOMETRIC_SETUP
            assertEquals(OnboardingStep.BIOMETRIC_SETUP, vm.state.value.step)
            vm.processIntent(OnboardingIntent.SetupBiometric)

            // ZERODHA_LOGIN
            assertEquals(OnboardingStep.ZERODHA_LOGIN, vm.state.value.step)
            vm.processIntent(OnboardingIntent.OnLoginSuccess("token"))
            advanceUntilIdle()

            // ACCOUNT_CONFIRMATION
            assertEquals(OnboardingStep.ACCOUNT_CONFIRMATION, vm.state.value.step)
            vm.processIntent(OnboardingIntent.ConfirmAndStart)
            advanceUntilIdle()

            // COMPLETE
            assertEquals(OnboardingStep.COMPLETE, vm.state.value.step)
        }
}
