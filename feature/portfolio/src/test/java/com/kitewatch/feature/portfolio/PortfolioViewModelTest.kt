package com.kitewatch.feature.portfolio

import app.cash.turbine.test
import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.PnlSummary
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.usecase.fund.GetFundBalanceUseCase
import com.kitewatch.domain.usecase.portfolio.CalculatePnlUseCase
import com.kitewatch.ui.component.DateRangePreset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val zeroPnl =
        PnlSummary(
            realizedPnl = Paisa.ZERO,
            totalSellValue = Paisa.ZERO,
            totalBuyCostOfSoldLots = Paisa.ZERO,
            totalCharges = Paisa.ZERO,
            chargeBreakdown =
                ChargeBreakdown(
                    stt = Paisa.ZERO,
                    exchangeTxn = Paisa.ZERO,
                    sebiCharges = Paisa.ZERO,
                    stampDuty = Paisa.ZERO,
                    gst = Paisa.ZERO,
                ),
            dateRange = LocalDate.now()..LocalDate.now(),
        )

    private lateinit var calculatePnlUseCase: CalculatePnlUseCase
    private lateinit var getFundBalanceUseCase: GetFundBalanceUseCase
    private lateinit var alertRepository: AlertRepository
    private lateinit var orderRepository: OrderRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        calculatePnlUseCase = mockk()
        getFundBalanceUseCase = mockk()
        alertRepository = mockk()
        orderRepository = mockk()

        // Safe defaults
        every { calculatePnlUseCase.execute(any(), any()) } returns flowOf(zeroPnl)
        every { getFundBalanceUseCase.execute() } returns flowOf(Paisa.ZERO)
        every { alertRepository.observeUnacknowledged() } returns flowOf(emptyList())
        every { orderRepository.observeAll() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        PortfolioViewModel(
            calculatePnlUseCase = calculatePnlUseCase,
            getFundBalanceUseCase = getFundBalanceUseCase,
            alertRepository = alertRepository,
            orderRepository = orderRepository,
        )

    @Test
    fun `initial state has isLoading true`() =
        runTest {
            val vm = buildViewModel()
            // Before any coroutines advance, isLoading starts true
            assertTrue(vm.state.value.isLoading)
        }

    @Test
    fun `after pnl emission isLoading is false and pnlSummary populated`() =
        runTest {
            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.state.value.isLoading)
            assertNotNull(vm.state.value.pnlSummary)
        }

    @Test
    fun `SelectDateRange intent updates selectedRange in state`() =
        runTest {
            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.processIntent(PortfolioIntent.SelectDateRange(DateRangePreset.THIS_MONTH))
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(DateRangePreset.THIS_MONTH, vm.state.value.selectedRange)
        }

    @Test
    fun `showSetupChecklist is true when order list is empty`() =
        runTest {
            every { orderRepository.observeAll() } returns flowOf(emptyList())
            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.state.value.showSetupChecklist)
        }

    @Test
    fun `showSetupChecklist is false when orders exist`() =
        runTest {
            every { orderRepository.observeAll() } returns flowOf(listOf(mockk<Order>()))
            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.state.value.showSetupChecklist)
        }

    @Test
    fun `SelectDateRange cancels prior pnl job and relaunches`() =
        runTest {
            var collectCount = 0
            every { calculatePnlUseCase.execute(any(), any()) } returns
                kotlinx.coroutines.flow.flow {
                    collectCount++
                    emit(zeroPnl)
                }
            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.processIntent(PortfolioIntent.SelectDateRange(DateRangePreset.ALL_TIME))
            testDispatcher.scheduler.advanceUntilIdle()

            // Once for init + once for the new range
            assertTrue(collectCount >= 2)
        }

    @Test
    fun `RefreshSync intent emits NavigateToSync side effect`() =
        runTest {
            val vm = buildViewModel()
            vm.sideEffect.test {
                vm.processIntent(PortfolioIntent.RefreshSync)
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(PortfolioSideEffect.NavigateToSync, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `DismissAlert calls alertRepository acknowledge`() =
        runTest {
            coEvery { alertRepository.acknowledge(any(), any()) } returns Unit
            val vm = buildViewModel()

            vm.processIntent(PortfolioIntent.DismissAlert(alertId = 42L))
            testDispatcher.scheduler.advanceUntilIdle()

            io.mockk.coVerify { alertRepository.acknowledge(42L, any()) }
        }

    @Test
    fun `error in pnl flow sets error string in state`() =
        runTest {
            every { calculatePnlUseCase.execute(any(), any()) } returns
                kotlinx.coroutines.flow.flow {
                    throw java.io.IOException("network error")
                }
            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.isLoading)
        }
}
