package com.kitewatch.domain.usecase.orders

import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.OrderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.time.LocalDate

class ImportCsvUseCaseTest {
    private val csvParsePort = mockk<CsvParsePort>()
    private val preImportBackupPort = mockk<PreImportBackupPort>()
    private val importTransactionPort = mockk<ImportTransactionPort>(relaxed = true)
    private val orderRepository = mockk<OrderRepository>()
    private val chargeRateRepository = mockk<ChargeRateRepository>()

    private lateinit var useCase: ImportCsvUseCase

    private val dummyStream = InputStream.nullInputStream()

    @Before
    fun setUp() {
        useCase =
            ImportCsvUseCase(
                csvParsePort = csvParsePort,
                preImportBackupPort = preImportBackupPort,
                importTransactionPort = importTransactionPort,
                orderRepository = orderRepository,
                chargeRateRepository = chargeRateRepository,
            )
        // Default: backup succeeds, no live charge rates
        coEvery { preImportBackupPort.createLocalBackup(any()) } returns Result.success(Unit)
        coEvery { chargeRateRepository.getCurrentRates() } returns null
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun order(zerodhaId: String) =
        Order(
            orderId = 0L,
            zerodhaOrderId = zerodhaId,
            stockCode = "INFY",
            stockName = "Infosys",
            orderType = OrderType.BUY,
            quantity = 5,
            price = Paisa(150_000L),
            totalValue = Paisa(750_000L),
            tradeDate = LocalDate.of(2024, 1, 15),
            exchange = Exchange.NSE,
            settlementId = null,
            source = OrderSource.CSV_IMPORT,
        )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `valid CSV with 5 orders and 2 duplicates returns correct counts`() =
        runTest {
            val parsedOrders = (1..5).map { order("ORD$it") }
            coEvery { csvParsePort.parse(any()) } returns CsvParsePortResult.Success(parsedOrders)

            // ORD3 and ORD4 already exist in the database
            val existingOrders = listOf(order("ORD3"), order("ORD4"))
            coEvery { orderRepository.getAll() } returns existingOrders

            val result = useCase.execute(dummyStream, "AB1234")

            assertTrue(result.isSuccess)
            val importResult = result.getOrThrow()
            assertEquals(3, importResult.newOrderCount)
            assertEquals(2, importResult.skippedDuplicateCount)
            // estimatedChargesCount = 3 because no live rates (getCurrentRates returns null)
            assertEquals(3, importResult.estimatedChargesCount)

            // Verify atomic write was called exactly once with the 3 new orders
            val ordersSlot = slot<List<Order>>()
            coVerify(exactly = 1) {
                importTransactionPort.runImport(capture(ordersSlot), any())
            }
            assertEquals(3, ordersSlot.captured.size)
            assertTrue(ordersSlot.captured.none { it.zerodhaOrderId in setOf("ORD3", "ORD4") })
        }

    @Test
    fun `invalid CSV returns failure with all row errors and writes nothing`() =
        runTest {
            val errors =
                listOf(
                    ImportRowError(rowNumber = 2, field = "trade_date", message = "Invalid date"),
                    ImportRowError(rowNumber = 3, field = "quantity", message = "Negative quantity"),
                )
            coEvery { csvParsePort.parse(any()) } returns CsvParsePortResult.Failure(errors)

            val result = useCase.execute(dummyStream, "AB1234")

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull() as ImportValidationException
            assertEquals(2, exception.errors.size)

            // BR-11: no data written
            coVerify(exactly = 0) { importTransactionPort.runImport(any(), any()) }
            coVerify(exactly = 0) { orderRepository.getAll() }
        }

    @Test
    fun `pre-import backup failure does not block import — warning only`() =
        runTest {
            coEvery { preImportBackupPort.createLocalBackup(any()) } returns
                Result.failure(RuntimeException("Drive unavailable"))

            val parsedOrders = listOf(order("ORD1"))
            coEvery { csvParsePort.parse(any()) } returns CsvParsePortResult.Success(parsedOrders)
            coEvery { orderRepository.getAll() } returns emptyList()

            val result = useCase.execute(dummyStream, "AB1234")

            // Import must succeed despite backup failure
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrThrow().newOrderCount)
            coVerify(exactly = 1) { importTransactionPort.runImport(any(), any()) }
        }

    @Test
    fun `all orders already exist — returns zero new orders without writing`() =
        runTest {
            val parsedOrders = listOf(order("ORD1"), order("ORD2"))
            coEvery { csvParsePort.parse(any()) } returns CsvParsePortResult.Success(parsedOrders)
            coEvery { orderRepository.getAll() } returns parsedOrders

            val result = useCase.execute(dummyStream, "AB1234")

            assertTrue(result.isSuccess)
            val importResult = result.getOrThrow()
            assertEquals(0, importResult.newOrderCount)
            assertEquals(2, importResult.skippedDuplicateCount)
            assertEquals(0, importResult.estimatedChargesCount)

            coVerify(exactly = 0) { importTransactionPort.runImport(any(), any()) }
        }

    @Test
    fun `live charge rates used when available — estimatedChargesCount is zero`() =
        runTest {
            coEvery { chargeRateRepository.getCurrentRates() } returns
                ImportCsvUseCase.FALLBACK_CHARGE_RATES.copy(
                    fetchedAt = java.time.Instant.now(),
                )

            val parsedOrders = listOf(order("ORD1"))
            coEvery { csvParsePort.parse(any()) } returns CsvParsePortResult.Success(parsedOrders)
            coEvery { orderRepository.getAll() } returns emptyList()

            val result = useCase.execute(dummyStream, "AB1234")

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrThrow().estimatedChargesCount)
        }

    @Test
    fun `empty CSV file returns zero counts without writing`() =
        runTest {
            coEvery { csvParsePort.parse(any()) } returns CsvParsePortResult.Success(emptyList())
            coEvery { orderRepository.getAll() } returns emptyList()

            val result = useCase.execute(dummyStream, "AB1234")

            assertTrue(result.isSuccess)
            val importResult = result.getOrThrow()
            assertEquals(0, importResult.newOrderCount)
            assertEquals(0, importResult.skippedDuplicateCount)
            coVerify(exactly = 0) { importTransactionPort.runImport(any(), any()) }
        }
}
