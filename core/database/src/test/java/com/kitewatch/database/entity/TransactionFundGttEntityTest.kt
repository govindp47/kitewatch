package com.kitewatch.database.entity

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ---------------------------------------------------------------------------
// Minimal test-only DAOs
// ---------------------------------------------------------------------------

@Dao
internal interface TestTransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(tx: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC")
    fun getAll(): List<TransactionEntity>
}

@Dao
internal interface TestFundEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entry: FundEntryEntity): Long

    @Query("SELECT * FROM fund_entries WHERE is_confirmed = 1")
    fun getConfirmed(): List<FundEntryEntity>

    @Query("SELECT * FROM fund_entries WHERE is_confirmed = 0")
    fun getPending(): List<FundEntryEntity>
}

@Dao
internal interface TestGttRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(gtt: GttRecordEntity): Long

    @Query("SELECT * FROM gtt_records WHERE is_archived = 0")
    fun getActive(): List<GttRecordEntity>

    @Query("SELECT * FROM gtt_records WHERE is_archived = 1")
    fun getArchived(): List<GttRecordEntity>

    @Query("SELECT * FROM gtt_records WHERE id = :id")
    fun getById(id: Long): GttRecordEntity?
}

// ---------------------------------------------------------------------------
// Test-only database (exportSchema=false — isolated from production schema)
// ---------------------------------------------------------------------------

@Database(
    entities = [TransactionEntity::class, FundEntryEntity::class, GttRecordEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class TestTxFundGttDb : RoomDatabase() {
    abstract fun transactionDao(): TestTransactionDao

    abstract fun fundEntryDao(): TestFundEntryDao

    abstract fun gttRecordDao(): TestGttRecordDao
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun buildTransaction(
    type: String = "EQUITY_BUY",
    stockCode: String? = "INFY",
    date: String = "2024-03-01",
    amountPaisa: Long = -1_500_000L,
): TransactionEntity =
    TransactionEntity(
        type = type,
        stockCode = stockCode,
        amountPaisa = amountPaisa,
        transactionDate = date,
        description = "Buy 10 INFY @ ₹1,500.00",
        source = "SYSTEM",
    )

private fun buildFundEntry(
    entryType: String = "ADDITION",
    isConfirmed: Int = 1,
    gmailMessageId: String? = null,
): FundEntryEntity =
    FundEntryEntity(
        entryType = entryType,
        amountPaisa = 10_000_000L,
        entryDate = "2024-03-01",
        isConfirmed = isConfirmed,
        gmailMessageId = gmailMessageId,
    )

private fun buildGtt(
    stockCode: String = "INFY",
    isArchived: Int = 0,
    zerodhaGttId: Long? = null,
): GttRecordEntity =
    GttRecordEntity(
        stockCode = stockCode,
        triggerPricePaisa = 180_000L,
        quantity = 10,
        status = "ACTIVE",
        zerodhaGttId = zerodhaGttId,
        isArchived = isArchived,
    )

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransactionFundGttEntityTest {
    private lateinit var db: TestTxFundGttDb

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TestTxFundGttDb::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- TransactionEntity ---

    @Test
    fun transactionEntity_insertAndReadBack_succeeds() {
        val id = db.transactionDao().insert(buildTransaction())
        val retrieved = db.transactionDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("EQUITY_BUY", retrieved!!.type)
        assertEquals("INFY", retrieved.stockCode)
        assertEquals(-1_500_000L, retrieved.amountPaisa)
    }

    @Test
    fun transactionEntity_nullableFields_storedCorrectly() {
        val tx = buildTransaction(type = "FUND_ADDITION", stockCode = null, amountPaisa = 10_000_000L)
        val id = db.transactionDao().insert(tx)
        val retrieved = db.transactionDao().getById(id)
        assertNotNull(retrieved)
        assertNull(retrieved!!.stockCode)
        assertNull(retrieved.subType)
        assertNull(retrieved.referenceId)
        assertNull(retrieved.runningFundBalancePaisa)
    }

    @Test
    fun transactionEntity_hasNoUpdatedAtColumn() {
        // Structural assertion: TransactionEntity must not declare an updatedAt field (INV-10)
        val fields = TransactionEntity::class.java.declaredFields.map { it.name }
        assert("updatedAt" !in fields) {
            "TransactionEntity must not have an updatedAt field — it is append-only (INV-10)"
        }
    }

    @Test
    fun transactionEntity_multipleInserts_allRetrieved() {
        db.transactionDao().insert(buildTransaction(date = "2024-01-01"))
        db.transactionDao().insert(buildTransaction(type = "STT_CHARGE", date = "2024-01-02", amountPaisa = -297L))
        db.transactionDao().insert(
            buildTransaction(type = "EQUITY_SELL", date = "2024-01-03", amountPaisa = 1_800_000L),
        )
        assertEquals(3, db.transactionDao().getAll().size)
    }

    // --- FundEntryEntity ---

    @Test
    fun fundEntryEntity_insertAndReadBack_succeeds() {
        val id = db.fundEntryDao().insert(buildFundEntry())
        assert(id > 0)
        assertEquals(1, db.fundEntryDao().getConfirmed().size)
        assertEquals(0, db.fundEntryDao().getPending().size)
    }

    @Test
    fun fundEntryEntity_pendingEntry_filteredFromConfirmed() {
        db.fundEntryDao().insert(buildFundEntry(isConfirmed = 1))
        db.fundEntryDao().insert(buildFundEntry(isConfirmed = 0))

        assertEquals(1, db.fundEntryDao().getConfirmed().size)
        assertEquals(1, db.fundEntryDao().getPending().size)
    }

    @Test
    fun fundEntryEntity_gmailDetected_nullableFields_storedCorrectly() {
        val entry = buildFundEntry(isConfirmed = 0, gmailMessageId = "msg_abc123")
        val id = db.fundEntryDao().insert(entry)
        val pending = db.fundEntryDao().getPending()
        assertEquals(1, pending.size)
        assertEquals("msg_abc123", pending[0].gmailMessageId)
        assertEquals(0, pending[0].isConfirmed)
    }

    // --- GttRecordEntity ---

    @Test
    fun gttRecordEntity_insertAndReadBack_succeeds() {
        val id = db.gttRecordDao().insert(buildGtt())
        val retrieved = db.gttRecordDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("INFY", retrieved!!.stockCode)
        assertEquals(0, retrieved.isArchived)
    }

    @Test
    fun gttRecordEntity_archived_excludedFromActiveQuery() {
        db.gttRecordDao().insert(buildGtt(isArchived = 0))
        db.gttRecordDao().insert(buildGtt(stockCode = "TCS", isArchived = 1))

        assertEquals(1, db.gttRecordDao().getActive().size)
        assertEquals(1, db.gttRecordDao().getArchived().size)
        assertEquals("INFY", db.gttRecordDao().getActive()[0].stockCode)
    }

    @Test
    fun gttRecordEntity_pendingCreation_nullZerodhaGttId_storedCorrectly() {
        val gtt = buildGtt(zerodhaGttId = null).copy(status = "PENDING_CREATION")
        val id = db.gttRecordDao().insert(gtt)
        val retrieved = db.gttRecordDao().getById(id)
        assertNotNull(retrieved)
        assertNull(retrieved!!.zerodhaGttId)
        assertEquals("PENDING_CREATION", retrieved.status)
    }

    @Test
    fun gttRecordEntity_hasIsArchivedFlag() {
        val fields = GttRecordEntity::class.java.declaredFields.map { it.name }
        assert("isArchived" in fields) {
            "GttRecordEntity must have isArchived field for soft delete"
        }
    }
}
