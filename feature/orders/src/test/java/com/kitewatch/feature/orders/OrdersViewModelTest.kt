package com.kitewatch.feature.orders

import androidx.paging.PagingData
import app.cash.turbine.test
import com.kitewatch.domain.model.SyncResult
import com.kitewatch.domain.usecase.SyncOrdersUseCase
import com.kitewatch.feature.orders.usecase.GetOrdersPagedUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getOrdersPagedUseCase: GetOrdersPagedUseCase
    private lateinit var syncOrdersUseCase: SyncOrdersUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getOrdersPagedUseCase = mockk()
        syncOrdersUseCase = mockk()

        every { getOrdersPagedUseCase.execute() } returns flowOf(PagingData.empty())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        OrdersViewModel(
            getOrdersPagedUseCase = getOrdersPagedUseCase,
            syncOrdersUseCase = syncOrdersUseCase,
        )

    @Test
    fun `initial state is not syncing with ALL_TIME range`() =
        runTest {
            val vm = buildViewModel()
            assertFalse(vm.state.value.isSyncing)
            assertEquals(com.kitewatch.ui.component.DateRangePreset.ALL_TIME, vm.state.value.selectedRange)
        }

    @Test
    fun `SyncNow intent calls syncOrdersUseCase and emits ShowSnackbar`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns Result.success(SyncResult.Success(3, 1))

            val vm = buildViewModel()

            vm.sideEffect.test {
                vm.processIntent(OrdersIntent.SyncNow)
                testDispatcher.scheduler.advanceUntilIdle()

                // First: "Syncing…"
                val first = awaitItem() as OrdersSideEffect.ShowSnackbar
                assertEquals("Syncing…", first.message)

                // Second: completion message with count
                val second = awaitItem() as OrdersSideEffect.ShowSnackbar
                assertTrue(second.message.contains("3"))

                cancelAndIgnoreRemainingEvents()
            }

            coVerify { syncOrdersUseCase.execute() }
        }

    @Test
    fun `SyncNow with NoNewOrders emits appropriate snackbar`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns Result.success(SyncResult.NoNewOrders)

            val vm = buildViewModel()

            vm.sideEffect.test {
                vm.processIntent(OrdersIntent.SyncNow)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() // "Syncing…"
                val completion = awaitItem() as OrdersSideEffect.ShowSnackbar
                assertTrue(completion.message.contains("no new orders"))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SyncNow with Skipped result emits skipped message`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns Result.success(SyncResult.Skipped("Weekend"))

            val vm = buildViewModel()

            vm.sideEffect.test {
                vm.processIntent(OrdersIntent.SyncNow)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() // "Syncing…"
                val completion = awaitItem() as OrdersSideEffect.ShowSnackbar
                assertTrue(completion.message.contains("skipped"))

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SyncNow on failure emits error snackbar`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } throws RuntimeException("API error")

            val vm = buildViewModel()

            vm.sideEffect.test {
                vm.processIntent(OrdersIntent.SyncNow)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() // "Syncing…"
                val error = awaitItem() as OrdersSideEffect.ShowSnackbar
                assertTrue(error.message.contains("API error") || error.message == "Sync failed")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isSyncing is false after sync completes`() =
        runTest {
            coEvery { syncOrdersUseCase.execute() } returns Result.success(SyncResult.NoNewOrders)

            val vm = buildViewModel()
            vm.processIntent(OrdersIntent.SyncNow)
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.state.value.isSyncing)
        }

    @Test
    fun `SelectDateRange updates selectedRange in state`() =
        runTest {
            val vm = buildViewModel()
            vm.processIntent(OrdersIntent.SelectDateRange(com.kitewatch.ui.component.DateRangePreset.THIS_MONTH))

            assertEquals(
                com.kitewatch.ui.component.DateRangePreset.THIS_MONTH,
                vm.state.value.selectedRange,
            )
        }

    @Test
    fun `duplicate SyncNow while syncing is ignored`() =
        runTest {
            var callCount = 0
            coEvery { syncOrdersUseCase.execute() } coAnswers {
                callCount++
                Result.success(SyncResult.NoNewOrders)
            }

            val vm = buildViewModel()

            // isSyncing is set synchronously before the coroutine launches
            vm.processIntent(OrdersIntent.SyncNow)
            assertTrue(vm.state.value.isSyncing)

            // Second intent should be rejected by the isSyncing guard
            vm.processIntent(OrdersIntent.SyncNow)
            testDispatcher.scheduler.advanceUntilIdle()

            // Only one actual sync call despite two intents
            assertEquals(1, callCount)
        }
}
