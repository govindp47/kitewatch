package com.kitewatch.feature.holdings

import app.cash.turbine.test
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.usecase.holdings.GetHoldingsUseCase
import com.kitewatch.domain.usecase.holdings.UpdateProfitTargetUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HoldingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getHoldingsUseCase: GetHoldingsUseCase
    private lateinit var updateProfitTargetUseCase: UpdateProfitTargetUseCase
    private lateinit var gttRepository: GttRepository

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun makeHolding(
        id: Long,
        stockCode: String,
        stockName: String = "$stockCode Ltd",
        quantity: Int = 10,
    ) = Holding(
        holdingId = id,
        stockCode = stockCode,
        stockName = stockName,
        quantity = quantity,
        avgBuyPrice = Paisa(100_00L),
        investedAmount = Paisa(1000_00L),
        totalBuyCharges = Paisa(10_00L),
        profitTarget = ProfitTarget.Percentage(500),
        targetSellPrice = Paisa(105_00L),
        createdAt = now,
        updatedAt = now,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getHoldingsUseCase = mockk()
        updateProfitTargetUseCase = mockk()
        gttRepository = mockk()

        every { getHoldingsUseCase.execute() } returns flowOf(emptyList())
        every { gttRepository.observeActive() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        HoldingsViewModel(
            getHoldingsUseCase = getHoldingsUseCase,
            updateProfitTargetUseCase = updateProfitTargetUseCase,
            gttRepository = gttRepository,
        )

    @Test
    fun `initial state has isLoading true`() =
        runTest {
            val vm = buildViewModel()
            assertTrue(vm.state.value.isLoading)
        }

    @Test
    fun `GetHoldingsUseCase emitting 3 holdings maps to 3 items in state`() =
        runTest {
            val holdings =
                listOf(
                    makeHolding(1L, "INFY"),
                    makeHolding(2L, "TCS"),
                    makeHolding(3L, "HDFC"),
                )
            every { getHoldingsUseCase.execute() } returns flowOf(holdings)

            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.state.value.isLoading)
            assertEquals(3, vm.state.value.holdings.size)
            assertEquals(
                "INFY",
                vm.state.value.holdings[0]
                    .stockCode,
            )
            assertEquals(
                "TCS",
                vm.state.value.holdings[1]
                    .stockCode,
            )
            assertEquals(
                "HDFC",
                vm.state.value.holdings[2]
                    .stockCode,
            )
        }

    @Test
    fun `ToggleExpand expands then collapses a holding`() =
        runTest {
            every { getHoldingsUseCase.execute() } returns flowOf(listOf(makeHolding(1L, "INFY")))

            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(
                vm.state.value.holdings
                    .first()
                    .isExpanded,
            )

            vm.processIntent(HoldingsIntent.ToggleExpand("INFY"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(
                vm.state.value.holdings
                    .first()
                    .isExpanded,
            )

            vm.processIntent(HoldingsIntent.ToggleExpand("INFY"))
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(
                vm.state.value.holdings
                    .first()
                    .isExpanded,
            )
        }

    @Test
    fun `EditProfitTarget sets editingStockCode in state`() =
        runTest {
            every { getHoldingsUseCase.execute() } returns flowOf(listOf(makeHolding(1L, "INFY")))

            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertNull(vm.state.value.editingStockCode)

            vm.processIntent(HoldingsIntent.EditProfitTarget("INFY"))

            assertEquals("INFY", vm.state.value.editingStockCode)
        }

    @Test
    fun `DismissEditSheet clears editingStockCode`() =
        runTest {
            val vm = buildViewModel()
            vm.processIntent(HoldingsIntent.EditProfitTarget("INFY"))
            assertEquals("INFY", vm.state.value.editingStockCode)

            vm.processIntent(HoldingsIntent.DismissEditSheet("INFY"))

            assertNull(vm.state.value.editingStockCode)
        }

    @Test
    fun `SaveProfitTarget calls use case and emits ShowSnackbar on success`() =
        runTest {
            coEvery { updateProfitTargetUseCase.execute(any(), any()) } returns Unit
            every { getHoldingsUseCase.execute() } returns flowOf(listOf(makeHolding(1L, "INFY")))

            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.sideEffect.test {
                vm.processIntent(HoldingsIntent.SaveProfitTarget("INFY", ProfitTarget.Percentage(500)))
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is HoldingsSideEffect.ShowSnackbar)
                cancelAndIgnoreRemainingEvents()
            }

            coVerify { updateProfitTargetUseCase.execute("INFY", ProfitTarget.Percentage(500)) }
            assertNull(vm.state.value.editingStockCode)
        }

    @Test
    fun `SaveProfitTarget emits ShowSnackbar with error on failure`() =
        runTest {
            coEvery { updateProfitTargetUseCase.execute(any(), any()) } throws RuntimeException("DB error")

            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            vm.sideEffect.test {
                vm.processIntent(HoldingsIntent.SaveProfitTarget("INFY", ProfitTarget.Percentage(500)))
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem() as HoldingsSideEffect.ShowSnackbar
                assertTrue(effect.message.contains("DB error") || effect.message == "Failed to save target")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `NavigateToGtt intent emits NavigateToGtt side effect`() =
        runTest {
            val vm = buildViewModel()

            vm.sideEffect.test {
                vm.processIntent(HoldingsIntent.NavigateToGtt)
                testDispatcher.scheduler.advanceUntilIdle()

                assertEquals(HoldingsSideEffect.NavigateToGtt, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `GTT record is linked to matching holding by stockCode`() =
        runTest {
            val holding = makeHolding(1L, "INFY")
            val gtt =
                GttRecord(
                    gttId = 10L,
                    zerodhaGttId = "Z123",
                    stockCode = "INFY",
                    triggerPrice = Paisa(105_00L),
                    quantity = 10,
                    status = GttStatus.ACTIVE,
                    isAppManaged = true,
                    lastSyncedAt = now,
                )
            every { getHoldingsUseCase.execute() } returns flowOf(listOf(holding))
            every { gttRepository.observeActive() } returns flowOf(listOf(gtt))

            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val uiModel =
                vm.state.value.holdings
                    .first()
            assertNotNull(uiModel.linkedGttStatus)
            assertEquals("Active", uiModel.linkedGttStatus!!.statusLabel)
        }

    @Test
    fun `error in holdings flow sets error in state`() =
        runTest {
            every { getHoldingsUseCase.execute() } returns
                kotlinx.coroutines.flow.flow {
                    throw java.io.IOException("DB failure")
                }

            val vm = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull(vm.state.value.error)
            assertFalse(vm.state.value.isLoading)
        }
}
