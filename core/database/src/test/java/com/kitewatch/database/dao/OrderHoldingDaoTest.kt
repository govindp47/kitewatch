package com.kitewatch.database.dao

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.kitewatch.database.entity.HoldingEntity
import com.kitewatch.database.entity.OrderEntity
import com.kitewatch.database.entity.OrderHoldingEntity
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

// ---------------------------------------------------------------------------
// Test-only database — mirrors production schema for these three tables.
// exportSchema=false keeps it isolated from v1 schema JSON.
// ---------------------------------------------------------------------------

@Database(
    entities = [OrderEntity::class, HoldingEntity::class, OrderHoldingEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class OrderHoldingTestDb : RoomDatabase() {
    abstract fun orderDao(): OrderDao

    abstract fun holdingDao(): HoldingDao

    abstract fun orderHoldingDao(): OrderHoldingDao
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun order(
    zerodhaId: String,
    stockCode: String = "INFY",
    orderType: String = "BUY",
    tradeDate: String = "2024-03-01",
) = OrderEntity(
    zerodhaOrderId = zerodhaId,
    stockCode = stockCode,
    stockName = "Test Corp",
    orderType = orderType,
    quantity = 10,
    pricePaisa = 100_000L,
    totalValuePaisa = 1_000_000L,
    tradeDate = tradeDate,
    exchange = "NSE",
)

private fun holding(
    stockCode: String,
    quantity: Int = 10,
) = HoldingEntity(
    stockCode = stockCode,
    stockName = "$stockCode Corp",
    quantity = quantity,
    avgBuyPricePaisa = 100_000L,
    investedAmountPaisa = 1_000_000L,
    targetSellPricePaisa = 110_000L,
)

// ---------------------------------------------------------------------------
// OrderDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OrderDaoTest {
    private lateinit var db: OrderHoldingTestDb
    private lateinit var orderDao: OrderDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    OrderHoldingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        orderDao = db.orderDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_returnsMinusOneOnDuplicateZerodhaOrderId() =
        runBlocking {
            orderDao.insert(order("ORD-001"))
            val result = orderDao.insert(order("ORD-001"))
            assertEquals(-1L, result)
        }

    @Test
    fun insert_returnsPositiveIdOnSuccess() =
        runBlocking {
            val id = orderDao.insert(order("ORD-001"))
            assertTrue(id > 0)
        }

    @Test
    fun existsByZerodhaId_returnsFalseBeforeInsert() =
        runBlocking {
            assertFalse(orderDao.existsByZerodhaId("ORD-999"))
        }

    @Test
    fun existsByZerodhaId_returnsTrueAfterInsert() =
        runBlocking {
            orderDao.insert(order("ORD-001"))
            assertTrue(orderDao.existsByZerodhaId("ORD-001"))
        }

    @Test
    fun observeAll_emitsInsertedOrdersNewestFirst() =
        runBlocking {
            orderDao.insert(order("ORD-A", tradeDate = "2024-01-01"))
            orderDao.insert(order("ORD-B", tradeDate = "2024-03-01"))
            val result = orderDao.observeAll().first()
            assertEquals(2, result.size)
            assertEquals("ORD-B", result[0].zerodhaOrderId)
            assertEquals("ORD-A", result[1].zerodhaOrderId)
        }

    @Test
    fun observeByDateRange_returnsOnlyOrdersInRange() =
        runBlocking {
            orderDao.insert(order("ORD-JAN", tradeDate = "2024-01-15"))
            orderDao.insert(order("ORD-FEB", tradeDate = "2024-02-15"))
            orderDao.insert(order("ORD-MAR", tradeDate = "2024-03-15"))
            val result = orderDao.observeByDateRange("2024-02-01", "2024-02-28").first()
            assertEquals(1, result.size)
            assertEquals("ORD-FEB", result[0].zerodhaOrderId)
        }

    @Test
    fun getAll_returnsAllOrders() =
        runBlocking {
            orderDao.insertAll(listOf(order("ORD-1"), order("ORD-2"), order("ORD-3")))
            assertEquals(3, orderDao.getAll().size)
        }

    @Test
    fun insertAll_ignoresDuplicates() =
        runBlocking {
            val results = orderDao.insertAll(listOf(order("ORD-1"), order("ORD-1"), order("ORD-2")))
            assertEquals(3, results.size)
            assertEquals(-1L, results[1])
            assertEquals(2, orderDao.getAll().size)
        }

    @Test
    fun getByStockCode_returnsOnlyMatchingStock() =
        runBlocking {
            orderDao.insert(order("ORD-1", stockCode = "INFY"))
            orderDao.insert(order("ORD-2", stockCode = "TCS"))
            orderDao.insert(order("ORD-3", stockCode = "INFY"))
            val result = orderDao.getByStockCode("INFY")
            assertEquals(2, result.size)
            assertTrue(result.all { it.stockCode == "INFY" })
        }

    @Test
    fun getBuyOrdersByStockCode_excludesSellOrders() =
        runBlocking {
            orderDao.insert(order("ORD-BUY", stockCode = "INFY", orderType = "BUY"))
            orderDao.insert(order("ORD-SELL", stockCode = "INFY", orderType = "SELL"))
            val result = orderDao.getBuyOrdersByStockCode("INFY")
            assertEquals(1, result.size)
            assertEquals("BUY", result[0].orderType)
        }
}

// ---------------------------------------------------------------------------
// HoldingDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HoldingDaoTest {
    private lateinit var db: OrderHoldingTestDb
    private lateinit var holdingDao: HoldingDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    OrderHoldingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        holdingDao = db.holdingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeActive_excludesZeroQuantityHoldings() =
        runBlocking {
            holdingDao.upsert(holding("INFY", quantity = 10))
            holdingDao.upsert(holding("TCS", quantity = 0))
            val result = holdingDao.observeActive().first()
            assertEquals(1, result.size)
            assertEquals("INFY", result[0].stockCode)
        }

    @Test
    fun observeActive_emitsEmptyWhenNoActiveHoldings() =
        runBlocking {
            holdingDao.upsert(holding("INFY", quantity = 0))
            val result = holdingDao.observeActive().first()
            assertTrue(result.isEmpty())
        }

    @Test
    fun upsert_replacesExistingOnStockCodeConflict() =
        runBlocking {
            holdingDao.upsert(holding("INFY", quantity = 10))
            holdingDao.upsert(holding("INFY", quantity = 20))
            val result = holdingDao.getByStockCode("INFY")
            assertEquals(20, result?.quantity)
        }

    @Test
    fun getByStockCode_returnsNullWhenNotFound() =
        runBlocking {
            assertNull(holdingDao.getByStockCode("NONEXISTENT"))
        }

    @Test
    fun updateQuantityAndPrice_updatesCorrectRow() =
        runBlocking {
            holdingDao.upsert(holding("INFY", quantity = 10))
            holdingDao.upsert(holding("TCS", quantity = 5))
            val now = System.currentTimeMillis()
            holdingDao.updateQuantityAndPrice(
                stockCode = "INFY",
                quantity = 15,
                avgBuyPricePaisa = 120_000L,
                investedAmountPaisa = 1_800_000L,
                updatedAt = now,
            )
            val infy = holdingDao.getByStockCode("INFY")!!
            assertEquals(15, infy.quantity)
            assertEquals(120_000L, infy.avgBuyPricePaisa)
            assertEquals(1_800_000L, infy.investedAmountPaisa)
            // TCS must be unchanged
            assertEquals(5, holdingDao.getByStockCode("TCS")?.quantity)
        }

    @Test
    fun getAll_returnsAllHoldingsIncludingZeroQuantity() =
        runBlocking {
            holdingDao.upsert(holding("INFY", quantity = 10))
            holdingDao.upsert(holding("TCS", quantity = 0))
            assertEquals(2, holdingDao.getAll().size)
        }
}

// ---------------------------------------------------------------------------
// OrderHoldingDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OrderHoldingDaoTest {
    private lateinit var db: OrderHoldingTestDb
    private lateinit var orderHoldingDao: OrderHoldingDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    OrderHoldingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        orderHoldingDao = db.orderHoldingDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertOrderAndHolding(
        orderId: String,
        stock: String,
    ): Pair<Long, Long> {
        val oId = db.orderDao().insert(order(orderId, stockCode = stock))
        val hId = db.holdingDao().upsert(holding(stock))
        return oId to hId
    }

    @Test
    fun getOrderIdsByHoldingId_returnsAssociatedOrderIds() =
        runBlocking {
            val (orderId, holdingId) = insertOrderAndHolding("ORD-1", "INFY")
            orderHoldingDao.insert(OrderHoldingEntity(orderId, holdingId, 10))
            val result = orderHoldingDao.getOrderIdsByHoldingId(holdingId)
            assertEquals(listOf(orderId), result)
        }

    @Test
    fun getOrderIdsByHoldingId_returnsEmptyWhenNoLinks() =
        runBlocking {
            assertTrue(orderHoldingDao.getOrderIdsByHoldingId(999L).isEmpty())
        }

    @Test
    fun insertAll_insertsMultipleLinks() =
        runBlocking {
            val (orderId1, holdingId1) = insertOrderAndHolding("ORD-1", "INFY")
            val (orderId2, holdingId2) = insertOrderAndHolding("ORD-2", "TCS")
            orderHoldingDao.insertAll(
                listOf(
                    OrderHoldingEntity(orderId1, holdingId1, 5),
                    OrderHoldingEntity(orderId2, holdingId2, 3),
                ),
            )
            assertEquals(1, orderHoldingDao.getOrderIdsByHoldingId(holdingId1).size)
            assertEquals(1, orderHoldingDao.getOrderIdsByHoldingId(holdingId2).size)
        }
}
