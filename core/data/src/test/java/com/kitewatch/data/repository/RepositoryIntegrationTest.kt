package com.kitewatch.data.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.kitewatch.database.dao.GttRecordDao
import com.kitewatch.database.dao.HoldingDao
import com.kitewatch.database.dao.OrderDao
import com.kitewatch.database.dao.TransactionDao
import com.kitewatch.database.entity.GttRecordEntity
import com.kitewatch.database.entity.HoldingEntity
import com.kitewatch.database.entity.OrderEntity
import com.kitewatch.database.entity.TransactionEntity
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionSource
import com.kitewatch.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Test-only in-memory database
// ---------------------------------------------------------------------------

@Database(
    entities = [OrderEntity::class, HoldingEntity::class, TransactionEntity::class, GttRecordEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class RepositoryTestDb : RoomDatabase() {
    abstract fun orderDao(): OrderDao

    abstract fun holdingDao(): HoldingDao

    abstract fun transactionDao(): TransactionDao

    abstract fun gttRecordDao(): GttRecordDao
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private val BASE_DATE = LocalDate.of(2024, 1, 15)
private val BASE_INSTANT = Instant.ofEpochMilli(1_700_000_000_000L)

private fun order(
    zerodhaId: String,
    stockCode: String = "INFY",
    orderType: OrderType = OrderType.BUY,
    tradeDate: LocalDate = BASE_DATE,
) = Order(
    orderId = 0L,
    zerodhaOrderId = zerodhaId,
    stockCode = stockCode,
    stockName = "Test Corp",
    orderType = orderType,
    quantity = 10,
    price = Paisa(150_000L),
    totalValue = Paisa(1_500_000L),
    tradeDate = tradeDate,
    exchange = Exchange.NSE,
    settlementId = null,
    source = OrderSource.SYNC,
)

private fun holding(
    stockCode: String = "INFY",
    quantity: Int = 10,
    avgBuyPricePaisa: Long = 150_000L,
) = Holding(
    holdingId = 0L,
    stockCode = stockCode,
    stockName = "Test Corp",
    quantity = quantity,
    avgBuyPrice = Paisa(avgBuyPricePaisa),
    investedAmount = Paisa(avgBuyPricePaisa * quantity),
    totalBuyCharges = Paisa(0L),
    profitTarget = ProfitTarget.Percentage(basisPoints = 500),
    targetSellPrice = Paisa(avgBuyPricePaisa),
    createdAt = BASE_INSTANT,
    updatedAt = BASE_INSTANT,
)

private fun transaction(
    type: TransactionType = TransactionType.EQUITY_BUY,
    stockCode: String? = "INFY",
    amountPaisa: Long = -1_500_000L,
    tradeDate: LocalDate = BASE_DATE,
) = Transaction(
    transactionId = 0L,
    type = type,
    referenceId = null,
    stockCode = stockCode,
    amount = Paisa(amountPaisa),
    transactionDate = tradeDate,
    description = "Test transaction",
    source = TransactionSource.SYNC,
)

private fun gttRecord(
    stockCode: String = "INFY",
    status: GttStatus = GttStatus.ACTIVE,
    zerodhaGttId: String? = "12345",
) = GttRecord(
    gttId = 0L,
    zerodhaGttId = zerodhaGttId,
    stockCode = stockCode,
    triggerPrice = Paisa(175_000L),
    quantity = 10,
    status = status,
    isAppManaged = true,
    lastSyncedAt = BASE_INSTANT,
)

// ---------------------------------------------------------------------------
// Test class
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryIntegrationTest {
    private lateinit var db: RepositoryTestDb
    private lateinit var orderRepo: OrderRepositoryImpl
    private lateinit var holdingRepo: HoldingRepositoryImpl
    private lateinit var transactionRepo: TransactionRepositoryImpl
    private lateinit var gttRepo: GttRepositoryImpl

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    RepositoryTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        orderRepo = OrderRepositoryImpl(db.orderDao())
        holdingRepo = HoldingRepositoryImpl(db.holdingDao())
        transactionRepo = TransactionRepositoryImpl(db.transactionDao())
        gttRepo = GttRepositoryImpl(db.gttRecordDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    // ─── OrderRepositoryImpl ──────────────────────────────────────────────────

    @Test
    fun `insertAll three orders then observeAll emits all three`() =
        runBlocking {
            val orders =
                listOf(
                    order("ORD001", "INFY", tradeDate = LocalDate.of(2024, 1, 15)),
                    order("ORD002", "TCS", tradeDate = LocalDate.of(2024, 1, 14)),
                    order("ORD003", "HDFC", tradeDate = LocalDate.of(2024, 1, 13)),
                )
            val ids = orderRepo.insertAll(orders)
            assertEquals(3, ids.size)
            assertTrue(ids.all { it > 0L })

            val emitted = orderRepo.observeAll().first()
            assertEquals(3, emitted.size)
            // Newest first
            assertEquals("ORD001", emitted[0].zerodhaOrderId)
            assertEquals("ORD002", emitted[1].zerodhaOrderId)
            assertEquals("ORD003", emitted[2].zerodhaOrderId)
        }

    @Test
    fun `insert duplicate zerodha_order_id returns -1 and does not duplicate`() =
        runBlocking {
            orderRepo.insert(order("ORD001"))
            val duplicateId = orderRepo.insert(order("ORD001"))
            assertEquals(-1L, duplicateId)

            val all = orderRepo.getAll()
            assertEquals(1, all.size)
        }

    @Test
    fun `existsByZerodhaId returns true after insert false before`() =
        runBlocking {
            assertFalse(orderRepo.existsByZerodhaId("ORD999"))
            orderRepo.insert(order("ORD999"))
            assertTrue(orderRepo.existsByZerodhaId("ORD999"))
        }

    @Test
    fun `getByStockCode filters correctly`() =
        runBlocking {
            orderRepo.insert(order("ORD001", "INFY"))
            orderRepo.insert(order("ORD002", "TCS"))
            orderRepo.insert(order("ORD003", "INFY"))

            val infyOrders = orderRepo.getByStockCode("INFY")
            assertEquals(2, infyOrders.size)
            assertTrue(infyOrders.all { it.stockCode == "INFY" })
        }

    @Test
    fun `observeByDateRange filters to inclusive range`() =
        runBlocking {
            orderRepo.insert(order("ORD001", tradeDate = LocalDate.of(2024, 1, 10)))
            orderRepo.insert(order("ORD002", tradeDate = LocalDate.of(2024, 1, 15)))
            orderRepo.insert(order("ORD003", tradeDate = LocalDate.of(2024, 1, 20)))

            val inRange =
                orderRepo
                    .observeByDateRange(
                        from = LocalDate.of(2024, 1, 12),
                        to = LocalDate.of(2024, 1, 17),
                    ).first()
            assertEquals(1, inRange.size)
            assertEquals("ORD002", inRange[0].zerodhaOrderId)
        }

    // ─── HoldingRepositoryImpl ────────────────────────────────────────────────

    @Test
    fun `upsert inserts then second upsert for same stockCode replaces first`() =
        runBlocking {
            holdingRepo.upsert(holding("INFY", quantity = 10, avgBuyPricePaisa = 150_000L))

            val first = holdingRepo.getByStockCode("INFY")
            assertEquals(10, first!!.quantity)

            holdingRepo.upsert(holding("INFY", quantity = 15, avgBuyPricePaisa = 160_000L))

            val updated = holdingRepo.getByStockCode("INFY")
            assertEquals(15, updated!!.quantity)
            assertEquals(Paisa(160_000L), updated.avgBuyPrice)
        }

    @Test
    fun `observeAll emits only holdings with quantity greater than zero`() =
        runBlocking {
            holdingRepo.upsert(holding("INFY", quantity = 10))
            holdingRepo.upsert(holding("TCS", quantity = 0)) // zero-quantity — excluded

            val emitted = holdingRepo.observeAll().first()
            assertEquals(1, emitted.size)
            assertEquals("INFY", emitted[0].stockCode)
        }

    @Test
    fun `updateQuantityAndPrice updates the correct holding`() =
        runBlocking {
            holdingRepo.upsert(holding("INFY", quantity = 10, avgBuyPricePaisa = 150_000L))

            holdingRepo.updateQuantityAndPrice(
                stockCode = "INFY",
                quantity = 20,
                avgBuyPrice = Paisa(155_000L),
                investedAmount = Paisa(3_100_000L),
                updatedAt = BASE_INSTANT.plusSeconds(60),
            )

            val updated = holdingRepo.getByStockCode("INFY")
            assertEquals(20, updated!!.quantity)
            assertEquals(Paisa(155_000L), updated.avgBuyPrice)
        }

    @Test
    fun `getAll returns all holdings including zero-quantity`() =
        runBlocking {
            holdingRepo.upsert(holding("INFY", quantity = 10))
            holdingRepo.upsert(holding("TCS", quantity = 0))

            val all = holdingRepo.getAll()
            assertEquals(2, all.size)
        }

    // ─── TransactionRepositoryImpl ────────────────────────────────────────────

    @Test
    fun `insert returns positive row id`() =
        runBlocking {
            val id = transactionRepo.insert(transaction())
            assertTrue(id > 0L)
        }

    @Test
    fun `insertAll inserts multiple transactions`() =
        runBlocking {
            val txns =
                listOf(
                    transaction(TransactionType.EQUITY_BUY, "INFY", -1_500_000L),
                    transaction(TransactionType.STT_CHARGE, null, -1500L),
                    transaction(TransactionType.FUND_DEPOSIT, null, 5_000_000L),
                )
            val ids = transactionRepo.insertAll(txns)
            assertEquals(3, ids.size)
            assertTrue(ids.all { it > 0L })

            val emitted = transactionRepo.observeAll().first()
            assertEquals(3, emitted.size)
        }

    @Test
    fun `observeByType filters to specified type`() =
        runBlocking {
            transactionRepo.insert(transaction(TransactionType.EQUITY_BUY))
            transactionRepo.insert(transaction(TransactionType.STT_CHARGE, null))
            transactionRepo.insert(transaction(TransactionType.EQUITY_BUY))

            val equityBuys = transactionRepo.observeByType(TransactionType.EQUITY_BUY).first()
            assertEquals(2, equityBuys.size)
            assertTrue(equityBuys.all { it.type == TransactionType.EQUITY_BUY })
        }

    // ─── GttRepositoryImpl ────────────────────────────────────────────────────

    @Test
    fun `upsert inserts GTT record`() =
        runBlocking {
            gttRepo.upsert(gttRecord("INFY"))

            val all = gttRepo.observeActive().first()
            assertEquals(1, all.size)
            assertEquals("INFY", all[0].stockCode)
            assertEquals(GttStatus.ACTIVE, all[0].status)
        }

    @Test
    fun `archive sets is_archived=1 and observeActive stops emitting the record`() =
        runBlocking {
            gttRepo.upsert(gttRecord("INFY"))
            val active = gttRepo.observeActive().first()
            assertEquals(1, active.size)

            val insertedId = active[0].gttId
            gttRepo.archive(insertedId, BASE_INSTANT.plusSeconds(10))

            val afterArchive = gttRepo.observeActive().first()
            assertEquals(0, afterArchive.size)
        }

    @Test
    fun `updateStatus changes domain status`() =
        runBlocking {
            gttRepo.upsert(gttRecord("INFY", status = GttStatus.ACTIVE))
            val id = gttRepo.observeActive().first()[0].gttId

            gttRepo.updateStatus(id, GttStatus.PENDING_UPDATE, BASE_INSTANT.plusSeconds(5))

            val updated = gttRepo.observeActive().first()[0]
            assertEquals(GttStatus.PENDING_UPDATE, updated.status)
        }

    @Test
    fun `getByStockCode returns null when no active GTT for stock`() =
        runBlocking {
            assertNull(gttRepo.getByStockCode("RELIANCE"))
        }

    @Test
    fun `getByStockCode returns null after archiving`() =
        runBlocking {
            gttRepo.upsert(gttRecord("INFY"))
            val id = gttRepo.observeActive().first()[0].gttId
            gttRepo.archive(id, BASE_INSTANT)

            assertNull(gttRepo.getByStockCode("INFY"))
        }

    @Test
    fun `second upsert for same GTT zerodha id replaces first`() =
        runBlocking {
            val record = gttRecord("INFY", zerodhaGttId = "99999")
            gttRepo.upsert(record)

            val firstId = gttRepo.observeActive().first()[0].gttId

            gttRepo.upsert(record.copy(gttId = firstId, triggerPrice = Paisa(200_000L)))

            val all = gttRepo.observeActive().first()
            assertEquals(1, all.size)
            assertEquals(Paisa(200_000L), all[0].triggerPrice)
        }
}
