package com.kitewatch.database.entity

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ---------------------------------------------------------------------------
// Minimal test-only DAOs — production DAOs are implemented in T-018/T-019
// ---------------------------------------------------------------------------

@Dao
internal interface TestOrderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(order: OrderEntity): Long

    @Delete
    fun delete(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :id")
    fun getById(id: Long): OrderEntity?
}

@Dao
internal interface TestHoldingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(holding: HoldingEntity): Long
}

@Dao
internal interface TestOrderHoldingDao {
    @Insert
    fun insert(link: OrderHoldingEntity)

    @Query("SELECT COUNT(*) FROM order_holdings WHERE order_id = :orderId")
    fun countByOrderId(orderId: Long): Int
}

// ---------------------------------------------------------------------------
// Test-only database — exportSchema=false keeps it isolated from v1 schema
// ---------------------------------------------------------------------------

@Database(
    entities = [OrderEntity::class, HoldingEntity::class, OrderHoldingEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class TestOrderHoldingsDb : RoomDatabase() {
    abstract fun orderDao(): TestOrderDao

    abstract fun holdingDao(): TestHoldingDao

    abstract fun orderHoldingDao(): TestOrderHoldingDao
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun buildOrder(
    zerodhaId: String,
    id: Long = 0,
): OrderEntity =
    OrderEntity(
        id = id,
        zerodhaOrderId = zerodhaId,
        stockCode = "INFY",
        stockName = "Infosys Limited",
        orderType = "BUY",
        quantity = 10,
        pricePaisa = 150_000L,
        totalValuePaisa = 1_500_000L,
        tradeDate = "2024-03-01",
        exchange = "NSE",
    )

private fun buildHolding(
    stockCode: String,
    id: Long = 0,
): HoldingEntity =
    HoldingEntity(
        id = id,
        stockCode = stockCode,
        stockName = "$stockCode Corp",
        quantity = 10,
        avgBuyPricePaisa = 150_000L,
        investedAmountPaisa = 1_500_000L,
        targetSellPricePaisa = 165_000L,
    )

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EntityConstraintTest {
    private lateinit var db: TestOrderHoldingsDb

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TestOrderHoldingsDb::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test(expected = SQLiteConstraintException::class)
    fun orderEntity_duplicateZerodhaOrderId_throwsConstraintException() {
        db.orderDao().insert(buildOrder("ORD-001"))
        // Same zerodha_order_id — UNIQUE constraint must fire
        db.orderDao().insert(buildOrder("ORD-001"))
    }

    @Test
    fun orderEntity_distinctZerodhaOrderIds_insertSuccessfully() {
        val id1 = db.orderDao().insert(buildOrder("ORD-001"))
        val id2 = db.orderDao().insert(buildOrder("ORD-002"))
        assert(id1 > 0)
        assert(id2 > 0)
        assert(id1 != id2)
    }

    @Test
    fun orderHoldingEntity_cascadeDelete_removesLinksWhenOrderDeleted() {
        val orderId = db.orderDao().insert(buildOrder("ORD-003"))
        val holdingId = db.holdingDao().insert(buildHolding("INFY"))
        db.orderHoldingDao().insert(OrderHoldingEntity(orderId, holdingId, 10))

        assertEquals(1, db.orderHoldingDao().countByOrderId(orderId))

        // Deleting the parent order must cascade-delete the junction row
        val order = db.orderDao().getById(orderId)!!
        db.orderDao().delete(order)

        assertEquals(0, db.orderHoldingDao().countByOrderId(orderId))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun holdingEntity_duplicateStockCode_throwsConstraintException() {
        db.holdingDao().insert(buildHolding("INFY"))
        // Same stock_code — UNIQUE constraint must fire
        db.holdingDao().insert(buildHolding("INFY"))
    }

    @Test
    fun holdingEntity_distinctStockCodes_insertSuccessfully() {
        val id1 = db.holdingDao().insert(buildHolding("INFY"))
        val id2 = db.holdingDao().insert(buildHolding("TCS"))
        assert(id1 > 0)
        assert(id2 > 0)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun orderHoldingEntity_compositePrimaryKey_preventsExactDuplicate() {
        val orderId = db.orderDao().insert(buildOrder("ORD-004"))
        val holdingId = db.holdingDao().insert(buildHolding("RELIANCE"))
        db.orderHoldingDao().insert(OrderHoldingEntity(orderId, holdingId, 5))
        db.orderHoldingDao().insert(OrderHoldingEntity(orderId, holdingId, 5))
    }
}
