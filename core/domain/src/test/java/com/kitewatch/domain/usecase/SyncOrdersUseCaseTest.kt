package com.kitewatch.domain.usecase

import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.SyncResult
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.AlertSeverity
import com.kitewatch.domain.repository.AlertType
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.repository.PersistentAlert
import com.kitewatch.domain.repository.RemoteHolding
import com.kitewatch.domain.repository.TransactionRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SyncOrdersUseCaseTest {
    // ─── Shared mocks ──────────────────────────────────────────────────────────

    private val kiteConnectRepo = mockk<KiteConnectRepository>()
    private val orderRepo = mockk<OrderRepository>()
    private val holdingRepo = mockk<HoldingRepository>()
    private val transactionRepo = mockk<TransactionRepository>()
    private val chargeRateRepo = mockk<ChargeRateRepository>()
    private val alertRepo = mockk<AlertRepository>()
    private val gttRepo = mockk<GttRepository>()

    /** Monday 2024-03-04 — a regular trading day. */
    private val weekdayClock = fixedClock(LocalDate.of(2024, 3, 4))

    /** Saturday 2024-03-02 — a weekend (no trading). */
    private val weekendClock = fixedClock(LocalDate.of(2024, 3, 2))

    private fun buildUseCase(
        clock: Clock = weekdayClock,
        mutex: Mutex = Mutex(),
    ) = SyncOrdersUseCase(
        kiteConnectRepo,
        orderRepo,
        holdingRepo,
        transactionRepo,
        chargeRateRepo,
        alertRepo,
        gttRepo,
        clock = clock,
        mutex = mutex,
    )

    // ─── Test 1: Weekend guard — no repo calls on Saturday ─────────────────────

    @Test
    fun `Skipped returned on weekend and no repository calls made`() =
        runTest {
            val result = buildUseCase(clock = weekendClock).execute()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() is SyncResult.Skipped)
            coVerify(exactly = 0) { kiteConnectRepo.fetchTodaysOrders() }
            coVerify(exactly = 0) { orderRepo.insert(any()) }
        }

    // ─── Test 2: Network failure on order fetch ─────────────────────────────────

    @Test
    fun `Failure returned when fetchTodaysOrders fails`() =
        runTest {
            coEvery { kiteConnectRepo.fetchTodaysOrders() } returns Result.failure(Exception("timeout"))

            val result = buildUseCase().execute()

            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as AppException).error
            assertTrue(error is AppError.NetworkError.Unexpected)
            coVerify(exactly = 0) { kiteConnectRepo.fetchHoldings() }
        }

    // ─── Test 3: No new orders — deduplicated to empty ─────────────────────────

    @Test
    fun `NoNewOrders returned when all remote orders already exist locally`() =
        runTest {
            val order = makeOrder("INFY")
            coEvery { kiteConnectRepo.fetchTodaysOrders() } returns Result.success(listOf(order))
            coEvery { orderRepo.existsByZerodhaId(any()) } returns true // already in DB

            val result = buildUseCase().execute()

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() is SyncResult.NoNewOrders)
            coVerify(exactly = 0) { kiteConnectRepo.fetchHoldings() }
        }

    // ─── Test 4: Holdings mismatch — alert inserted + Failure ──────────────────

    @Test
    fun `HoldingsMismatch failure and alert inserted when remote qty differs from local`() =
        runTest {
            val order = makeOrder("INFY")
            coEvery { kiteConnectRepo.fetchTodaysOrders() } returns Result.success(listOf(order))
            coEvery { orderRepo.existsByZerodhaId(any()) } returns false
            // Remote: INFY qty=10; Local: INFY qty=5 → mismatch
            coEvery { kiteConnectRepo.fetchHoldings() } returns
                Result.success(
                    listOf(RemoteHolding(tradingSymbol = "INFY", quantity = 10, product = "CNC")),
                )
            coEvery { holdingRepo.getAll() } returns
                listOf(
                    makeHolding("INFY", quantity = 5),
                )

            val alertSlot = slot<PersistentAlert>()
            coEvery { alertRepo.insert(capture(alertSlot)) } returns 1L

            val result = buildUseCase().execute()

            assertTrue(result.isFailure)
            val error = (result.exceptionOrNull() as AppException).error
            assertTrue(error is AppError.DomainError.HoldingsMismatch)
            assertEquals(AlertType.HOLDINGS_MISMATCH, alertSlot.captured.alertType)
            assertEquals(AlertSeverity.CRITICAL, alertSlot.captured.severity)
            coVerify(exactly = 0) { orderRepo.insert(any()) }
        }

    // ─── Test 5: Happy path — 3 new orders → Success ───────────────────────────

    @Test
    fun `Success returned with correct newOrderCount for 3 new orders`() =
        runTest {
            val orders =
                listOf(
                    makeOrder("INFY", orderId = 0L),
                    makeOrder("TCS", orderId = 0L),
                    makeOrder("WIPRO", orderId = 0L),
                )
            coEvery { kiteConnectRepo.fetchTodaysOrders() } returns Result.success(orders)
            coEvery { orderRepo.existsByZerodhaId(any()) } returns false
            coEvery { kiteConnectRepo.fetchHoldings() } returns Result.success(emptyList())
            coEvery { holdingRepo.getAll() } returns emptyList()
            coEvery { chargeRateRepo.getCurrentRates() } returns null

            coEvery { orderRepo.insert(any()) } returnsMany listOf(1L, 2L, 3L)
            coEvery { transactionRepo.insert(any()) } returns 1L
            coEvery { orderRepo.getAll() } returns orders.mapIndexed { i, o -> o.copy(orderId = (i + 1).toLong()) }
            coEvery { holdingRepo.upsert(any()) } just Runs

            val result = buildUseCase().execute()

            assertTrue(result.isSuccess)
            val success = result.getOrNull() as SyncResult.Success
            assertEquals(3, success.newOrderCount)
            assertEquals(0, success.updatedGttCount) // no charge rates → GTT eval skipped
        }

    // ─── Test 6: Mutex released on failure — second call not blocked ────────────

    @Test
    fun `Mutex released after failure so subsequent execute calls are not blocked`() =
        runTest {
            coEvery { kiteConnectRepo.fetchTodaysOrders() } returns Result.failure(Exception("net error"))

            val mutex = Mutex()
            val useCase = buildUseCase(mutex = mutex)

            val first = useCase.execute()
            // If mutex was not released, this call would deadlock (runTest timeout would trigger).
            val second = useCase.execute()

            assertFalse(mutex.isLocked)
            assertTrue(first.isFailure)
            assertTrue(second.isFailure)
        }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun makeOrder(
        stockCode: String,
        orderType: OrderType = OrderType.BUY,
        orderId: Long = 0L,
    ) = Order(
        orderId = orderId,
        zerodhaOrderId = "ZERODHA-$stockCode",
        stockCode = stockCode,
        stockName = "$stockCode Ltd",
        orderType = orderType,
        quantity = 10,
        price = Paisa(100_00L), // ₹100/share
        totalValue = Paisa(1_000_00L), // ₹1,000 total
        tradeDate = LocalDate.of(2024, 3, 4),
        exchange = Exchange.NSE,
        settlementId = null,
        source = OrderSource.SYNC,
    )

    private fun makeHolding(
        stockCode: String,
        quantity: Int,
    ) = com.kitewatch.domain.model.Holding(
        holdingId = 1L,
        stockCode = stockCode,
        stockName = "$stockCode Ltd",
        quantity = quantity,
        avgBuyPrice = Paisa(100_00L),
        investedAmount = Paisa(quantity * 100_00L),
        totalBuyCharges = Paisa.ZERO,
        profitTarget =
            com.kitewatch.domain.model.ProfitTarget
                .Percentage(500),
        targetSellPrice = Paisa(105_00L),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun fixedClock(date: LocalDate): Clock =
        Clock.fixed(date.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
}
