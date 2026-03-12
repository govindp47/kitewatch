package com.kitewatch.database.dao

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.kitewatch.database.entity.FundEntryEntity
import com.kitewatch.database.entity.GttRecordEntity
import com.kitewatch.database.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ---------------------------------------------------------------------------
// Test-only database
// ---------------------------------------------------------------------------

@Database(
    entities = [TransactionEntity::class, FundEntryEntity::class, GttRecordEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class TxnFundGttTestDb : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun fundEntryDao(): FundEntryDao

    abstract fun gttRecordDao(): GttRecordDao
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun txn(
    type: String = "EQUITY_BUY",
    date: String = "2024-03-01",
    amountPaisa: Long = -1_000_000L,
    stockCode: String? = "INFY",
) = TransactionEntity(
    type = type,
    transactionDate = date,
    amountPaisa = amountPaisa,
    stockCode = stockCode,
)

private fun fundEntry(
    entryType: String = "ADDITION",
    amountPaisa: Long = 500_000L,
    entryDate: String = "2024-03-01",
    isConfirmed: Int = 1,
    gmailMessageId: String? = null,
) = FundEntryEntity(
    entryType = entryType,
    amountPaisa = amountPaisa,
    entryDate = entryDate,
    isConfirmed = isConfirmed,
    gmailMessageId = gmailMessageId,
)

private fun gttRecord(
    stockCode: String,
    status: String = "PENDING_CREATION",
    isArchived: Int = 0,
) = GttRecordEntity(
    stockCode = stockCode,
    triggerPricePaisa = 200_000L,
    quantity = 10,
    status = status,
    isArchived = isArchived,
)

// ---------------------------------------------------------------------------
// TransactionDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransactionDaoTest {
    private lateinit var db: TxnFundGttTestDb
    private lateinit var dao: TransactionDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TxnFundGttTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.transactionDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_returnsPositiveId() =
        runBlocking {
            val id = dao.insert(txn())
            assertTrue(id > 0)
        }

    @Test
    fun observeAll_emitsThreeInDescendingDateOrder() =
        runBlocking {
            dao.insert(txn(date = "2024-01-01"))
            dao.insert(txn(date = "2024-03-01"))
            dao.insert(txn(date = "2024-02-01"))
            val result = dao.observeAll().first()
            assertEquals(3, result.size)
            assertEquals("2024-03-01", result[0].transactionDate)
            assertEquals("2024-02-01", result[1].transactionDate)
            assertEquals("2024-01-01", result[2].transactionDate)
        }

    @Test
    fun observeByType_returnsOnlyMatchingType() =
        runBlocking {
            dao.insert(txn(type = "EQUITY_BUY"))
            dao.insert(txn(type = "STT_CHARGE"))
            dao.insert(txn(type = "EQUITY_BUY"))
            val result = dao.observeByType("EQUITY_BUY").first()
            assertEquals(2, result.size)
            assertTrue(result.all { it.type == "EQUITY_BUY" })
        }

    @Test
    fun getByStockCode_returnsOnlyMatchingStock() =
        runBlocking {
            dao.insert(txn(stockCode = "INFY"))
            dao.insert(txn(stockCode = "TCS"))
            dao.insert(txn(stockCode = "INFY"))
            val result = dao.getByStockCode("INFY")
            assertEquals(2, result.size)
            assertTrue(result.all { it.stockCode == "INFY" })
        }

    @Test
    fun getTotalCredits_sumsPositiveAmounts() =
        runBlocking {
            dao.insert(txn(amountPaisa = 500_000L, stockCode = null)) // credit
            dao.insert(txn(amountPaisa = -200_000L)) // debit
            dao.insert(txn(amountPaisa = 300_000L, stockCode = null)) // credit
            val credits = dao.getTotalCredits()
            assertEquals(800_000L, credits)
        }

    @Test
    fun getTotalDebits_sumsAbsoluteNegativeAmounts() =
        runBlocking {
            dao.insert(txn(amountPaisa = 500_000L, stockCode = null))
            dao.insert(txn(amountPaisa = -200_000L))
            dao.insert(txn(amountPaisa = -150_000L))
            val debits = dao.getTotalDebits()
            assertEquals(350_000L, debits)
        }

    @Test
    fun getTotalCredits_returnsNullWhenTableEmpty() =
        runBlocking {
            assertNull(dao.getTotalCredits())
        }

    /** Verify at compile/reflection level that no @Update or @Delete exists on the implementation. */
    @Test
    fun transactionDao_hasNoUpdateOrDeleteMethods() {
        val daoClass = dao.javaClass
        val updateAnnotation = androidx.room.Update::class.java
        val deleteAnnotation = androidx.room.Delete::class.java
        val violations =
            daoClass.methods.filter { method ->
                method.isAnnotationPresent(updateAnnotation) ||
                    method.isAnnotationPresent(deleteAnnotation)
            }
        assertTrue(
            "TransactionDao must have no @Update or @Delete methods, found: ${violations.map { it.name }}",
            violations.isEmpty(),
        )
    }
}

// ---------------------------------------------------------------------------
// FundEntryDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FundEntryDaoTest {
    private lateinit var db: TxnFundGttTestDb
    private lateinit var dao: FundEntryDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TxnFundGttTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.fundEntryDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getPendingGmailEntries_returnsOnlyUnconfirmedRows() =
        runBlocking {
            dao.insert(fundEntry(isConfirmed = 1))
            dao.insert(fundEntry(isConfirmed = 0, gmailMessageId = "MSG-001"))
            dao.insert(fundEntry(isConfirmed = 0, gmailMessageId = "MSG-002"))
            val result = dao.getPendingGmailEntries()
            assertEquals(2, result.size)
            assertTrue(result.all { it.isConfirmed == 0 })
        }

    @Test
    fun observeConfirmed_excludesPendingEntries() =
        runBlocking {
            dao.insert(fundEntry(isConfirmed = 1, amountPaisa = 100_000L))
            dao.insert(fundEntry(isConfirmed = 0, gmailMessageId = "MSG-001"))
            val result = dao.observeConfirmed().first()
            assertEquals(1, result.size)
            assertEquals(1, result[0].isConfirmed)
        }

    @Test
    fun confirm_setsIsConfirmedOnCorrectRowOnly() =
        runBlocking {
            val id1 = dao.insert(fundEntry(isConfirmed = 0, gmailMessageId = "MSG-A"))
            val id2 = dao.insert(fundEntry(isConfirmed = 0, gmailMessageId = "MSG-B"))
            dao.confirm(id1)
            val pending = dao.getPendingGmailEntries()
            assertEquals(1, pending.size)
            assertEquals(id2, pending[0].id)
        }

    @Test
    fun getTotalConfirmedFunds_sumsOnlyConfirmedEntries() =
        runBlocking {
            dao.insert(fundEntry(amountPaisa = 200_000L, isConfirmed = 1))
            dao.insert(fundEntry(amountPaisa = 300_000L, isConfirmed = 1))
            dao.insert(fundEntry(amountPaisa = 999_999L, isConfirmed = 0, gmailMessageId = "MSG-X"))
            val total = dao.getTotalConfirmedFunds()
            assertEquals(500_000L, total)
        }
}

// ---------------------------------------------------------------------------
// GttRecordDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GttRecordDaoTest {
    private lateinit var db: TxnFundGttTestDb
    private lateinit var dao: GttRecordDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TxnFundGttTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.gttRecordDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeActive_excludesArchivedRecords() =
        runBlocking {
            dao.upsert(gttRecord("INFY", isArchived = 0))
            dao.upsert(gttRecord("TCS", isArchived = 1))
            val result = dao.observeActive().first()
            assertEquals(1, result.size)
            assertEquals("INFY", result[0].stockCode)
        }

    @Test
    fun observeActive_emitsEmptyWhenAllArchived() =
        runBlocking {
            dao.upsert(gttRecord("INFY", isArchived = 1))
            val result = dao.observeActive().first()
            assertTrue(result.isEmpty())
        }

    @Test
    fun archive_setsIsArchivedAndExcludesFromActive() =
        runBlocking {
            val id = dao.upsert(gttRecord("RELIANCE"))
            dao.archive(id, System.currentTimeMillis())
            val active = dao.observeActive().first()
            assertTrue(active.none { it.id == id })
        }

    @Test
    fun updateStatus_updatesOnlyTargetRow() =
        runBlocking {
            val id1 = dao.upsert(gttRecord("INFY", status = "PENDING_CREATION"))
            val id2 = dao.upsert(gttRecord("TCS", status = "PENDING_CREATION"))
            dao.updateStatus(id1, "ACTIVE", System.currentTimeMillis())
            val infy = dao.getActiveByStockCode("INFY")
            val tcs = dao.getActiveByStockCode("TCS")
            assertEquals("ACTIVE", infy?.status)
            assertEquals("PENDING_CREATION", tcs?.status)
        }

    @Test
    fun getActiveByStockCode_returnsNullWhenArchived() =
        runBlocking {
            val id = dao.upsert(gttRecord("WIPRO"))
            dao.archive(id, System.currentTimeMillis())
            assertNull(dao.getActiveByStockCode("WIPRO"))
        }
}
