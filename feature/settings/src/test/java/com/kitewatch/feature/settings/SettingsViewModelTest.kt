package com.kitewatch.feature.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.kitewatch.data.preferences.ThemePreferenceRepository
import com.kitewatch.domain.model.AccountBinding
import com.kitewatch.domain.model.FundEntry
import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.usecase.fund.AddFundEntryUseCase
import com.kitewatch.domain.usecase.fund.GetFundBalanceUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val themeRepo: ThemePreferenceRepository = mockk(relaxed = true)
    private val addFundEntryUseCase: AddFundEntryUseCase = mockk()
    private val getFundBalanceUseCase: GetFundBalanceUseCase = mockk()
    private val accountBindingRepository: AccountBindingRepository = mockk()
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { themeRepo.isDarkTheme } returns flowOf(false)
        every { getFundBalanceUseCase.execute() } returns flowOf(Paisa(0L))
        coEvery { accountBindingRepository.getBinding() } returns null
        // Context mock: packageManager returns a PackageInfo with versionName ""
        val pm = mockk<PackageManager>(relaxed = true)
        val pi = PackageInfo().also { it.versionName = "1.0.0" }
        every { context.packageManager } returns pm
        every { context.packageName } returns "com.kitewatch.test"
        every { pm.getPackageInfo("com.kitewatch.test", 0) } returns pi
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        SettingsViewModel(
            themePreferenceRepository = themeRepo,
            addFundEntryUseCase = addFundEntryUseCase,
            getFundBalanceUseCase = getFundBalanceUseCase,
            accountBindingRepository = accountBindingRepository,
            context = context,
        )

    @Test
    fun `initial fundBalance is formatted zero`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals("₹0.00", vm.state.value.fundBalance)
        }

    @Test
    fun `isDarkTheme reflects repository value`() =
        runTest {
            every { themeRepo.isDarkTheme } returns flowOf(true)
            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(vm.state.value.isDarkTheme)
        }

    @Test
    fun `zerodhaUserId is masked when binding present`() =
        runTest {
            coEvery { accountBindingRepository.getBinding() } returns
                AccountBinding(
                    userId = "AB1234",
                    userName = "Test User",
                    apiKey = "key",
                    boundAt = Instant.EPOCH,
                )
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals("AB**34", vm.state.value.zerodhaUserId)
        }

    @Test
    fun `zerodhaUserId remains dash when no binding`() =
        runTest {
            coEvery { accountBindingRepository.getBinding() } returns null
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals("—", vm.state.value.zerodhaUserId)
        }

    @Test
    fun `ShowFundEntrySheet intent opens sheet and clears error`() =
        runTest {
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(SettingsIntent.ShowFundEntrySheet)

            assertTrue(vm.state.value.showFundEntrySheet)
            assertNull(vm.state.value.fundEntryError)
        }

    @Test
    fun `DismissFundEntrySheet intent closes sheet`() =
        runTest {
            val vm = buildViewModel()
            vm.processIntent(SettingsIntent.ShowFundEntrySheet)
            vm.processIntent(SettingsIntent.DismissFundEntrySheet)

            assertFalse(vm.state.value.showFundEntrySheet)
        }

    @Test
    fun `AddFundEntry success closes sheet and clears saving flag`() =
        runTest {
            val entry =
                FundEntry(
                    entryId = 1L,
                    entryType = FundEntryType.DEPOSIT,
                    amount = Paisa(100_00L),
                    entryDate = LocalDate.now(),
                    note = null,
                    gmailMessageId = null,
                )
            coEvery {
                addFundEntryUseCase.execute(any(), any(), any(), any())
            } returns Result.success(entry)

            val vm = buildViewModel()
            vm.processIntent(SettingsIntent.ShowFundEntrySheet)
            vm.processIntent(
                SettingsIntent.AddFundEntry(
                    amount = Paisa(100_00L),
                    date = LocalDate.now(),
                    note = null,
                    entryType = FundEntryType.DEPOSIT,
                ),
            )
            advanceUntilIdle()

            assertFalse(vm.state.value.showFundEntrySheet)
            assertFalse(vm.state.value.isSavingFundEntry)
            assertNull(vm.state.value.fundEntryError)
        }

    @Test
    fun `AddFundEntry failure sets fundEntryError`() =
        runTest {
            coEvery {
                addFundEntryUseCase.execute(any(), any(), any(), any())
            } returns Result.failure(Exception("Amount must be positive"))

            val vm = buildViewModel()
            vm.processIntent(
                SettingsIntent.AddFundEntry(
                    amount = Paisa(0L),
                    date = LocalDate.now(),
                    note = null,
                    entryType = FundEntryType.DEPOSIT,
                ),
            )
            advanceUntilIdle()

            assertEquals("Amount must be positive", vm.state.value.fundEntryError)
            assertFalse(vm.state.value.isSavingFundEntry)
        }

    @Test
    fun `ToggleTheme calls setDarkTheme with inverted value`() =
        runTest {
            every { themeRepo.isDarkTheme } returns flowOf(false)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.processIntent(SettingsIntent.ToggleTheme)
            advanceUntilIdle()

            coVerify { themeRepo.setDarkTheme(true) }
        }

    @Test
    fun `fundBalance updates when use case emits new value`() =
        runTest {
            every { getFundBalanceUseCase.execute() } returns flowOf(Paisa(50_000_00L))
            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals("₹50,000.00", vm.state.value.fundBalance)
        }
}
