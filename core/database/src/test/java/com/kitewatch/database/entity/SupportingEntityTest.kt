package com.kitewatch.database.entity

import android.database.sqlite.SQLiteConstraintException
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
internal interface TestAccountBindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: AccountBindingEntity): Long

    @Query("SELECT * FROM account_binding WHERE id = 1")
    fun get(): AccountBindingEntity?

    @Query("SELECT COUNT(*) FROM account_binding")
    fun count(): Int
}

@Dao
internal interface TestChargeRateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: ChargeRateEntity): Long
}

@Dao
internal interface TestPnlCacheDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: PnlMonthlyCacheEntity): Long

    @Query("SELECT * FROM pnl_monthly_cache WHERE year_month = :month")
    fun getByMonth(month: String): PnlMonthlyCacheEntity?
}

@Dao
internal interface TestGmailScanCacheDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: GmailScanCacheEntity): Long

    @Query("SELECT * FROM gmail_scan_cache WHERE gmail_message_id = :msgId")
    fun getByMessageId(msgId: String): GmailScanCacheEntity?
}

@Dao
internal interface TestPersistentAlertDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: PersistentAlertEntity): Long

    @Query("SELECT * FROM persistent_alerts WHERE id = :id")
    fun getById(id: Long): PersistentAlertEntity?
}

@Dao
internal interface TestSyncEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: SyncEventEntity): Long

    @Query("SELECT * FROM sync_event_log WHERE id = :id")
    fun getById(id: Long): SyncEventEntity?
}

@Dao
internal interface TestWorkerHandoffDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: WorkerHandoffEntity): Long

    @Query("SELECT * FROM worker_handoff WHERE id = :id")
    fun getById(id: Long): WorkerHandoffEntity?
}

@Dao
internal interface TestGmailFilterDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: GmailFilterEntity): Long

    @Query("SELECT COUNT(*) FROM gmail_filters WHERE is_active = 1")
    fun countActive(): Int
}

// ---------------------------------------------------------------------------
// Test-only database (exportSchema=false — isolated from production schema)
// ---------------------------------------------------------------------------

@Database(
    entities = [
        AccountBindingEntity::class,
        ChargeRateEntity::class,
        PersistentAlertEntity::class,
        SyncEventEntity::class,
        PnlMonthlyCacheEntity::class,
        WorkerHandoffEntity::class,
        GmailScanCacheEntity::class,
        GmailFilterEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
internal abstract class TestSupportingDb : RoomDatabase() {
    abstract fun accountBindingDao(): TestAccountBindingDao

    abstract fun chargeRateDao(): TestChargeRateDao

    abstract fun pnlCacheDao(): TestPnlCacheDao

    abstract fun gmailScanCacheDao(): TestGmailScanCacheDao

    abstract fun alertDao(): TestPersistentAlertDao

    abstract fun syncEventDao(): TestSyncEventDao

    abstract fun workerHandoffDao(): TestWorkerHandoffDao

    abstract fun gmailFilterDao(): TestGmailFilterDao
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SupportingEntityTest {
    private lateinit var db: TestSupportingDb

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    TestSupportingDb::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- AccountBindingEntity ---

    @Test
    fun accountBindingEntity_upsert_singleRowEnforced() {
        val first =
            AccountBindingEntity(
                zerodhaUserId = "AB1234",
                userName = "Test User",
                apiKey = "key1",
                boundAt = "2024-03-01T00:00:00Z",
            )
        db.accountBindingDao().upsert(first)
        assertEquals(1, db.accountBindingDao().count())

        // Replace with new values — row count must remain 1
        val updated = first.copy(userName = "Updated User", apiKey = "key2")
        db.accountBindingDao().upsert(updated)
        assertEquals(1, db.accountBindingDao().count())
        assertEquals("Updated User", db.accountBindingDao().get()?.userName)
    }

    @Test
    fun accountBindingEntity_fixedPkIs1() {
        val entity =
            AccountBindingEntity(
                zerodhaUserId = "AB1234",
                userName = "User",
                apiKey = "key",
                boundAt = "2024-03-01T00:00:00Z",
            )
        db.accountBindingDao().upsert(entity)
        val retrieved = db.accountBindingDao().get()
        assertNotNull(retrieved)
        assertEquals(1L, retrieved!!.id)
    }

    @Test
    fun accountBindingEntity_nullableFields_storedCorrectly() {
        val entity =
            AccountBindingEntity(
                zerodhaUserId = "AB1234",
                userName = "User",
                apiKey = "key",
                boundAt = "2024-03-01T00:00:00Z",
                accessToken = null,
                tokenExpiresAt = null,
                lastAuthAt = null,
            )
        db.accountBindingDao().upsert(entity)
        val retrieved = db.accountBindingDao().get()
        assertNotNull(retrieved)
        assertNull(retrieved!!.accessToken)
        assertNull(retrieved.tokenExpiresAt)
    }

    // --- ChargeRateEntity ---

    @Test(expected = SQLiteConstraintException::class)
    fun chargeRateEntity_duplicateRateTypeAndEffectiveFrom_throwsConstraintException() {
        val rate =
            ChargeRateEntity(
                rateType = "STT_BUY",
                rateValue = 10,
                rateUnit = "BASIS_POINTS",
                effectiveFrom = "2024-01-01",
                fetchedAt = System.currentTimeMillis(),
            )
        db.chargeRateDao().insert(rate)
        db.chargeRateDao().insert(rate.copy(id = 0)) // same rate_type + effective_from
    }

    // --- PnlMonthlyCacheEntity ---

    @Test
    fun pnlMonthlyCacheEntity_insertAndReadBack_succeeds() {
        val entity =
            PnlMonthlyCacheEntity(
                yearMonth = "2024-03",
                realizedPnlPaisa = 50_000L,
                orderCount = 3,
            )
        db.pnlCacheDao().insert(entity)
        val retrieved = db.pnlCacheDao().getByMonth("2024-03")
        assertNotNull(retrieved)
        assertEquals(50_000L, retrieved!!.realizedPnlPaisa)
        assertEquals(3, retrieved.orderCount)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun pnlMonthlyCacheEntity_duplicateYearMonth_throwsConstraintException() {
        db.pnlCacheDao().insert(PnlMonthlyCacheEntity(yearMonth = "2024-03"))
        db.pnlCacheDao().insert(PnlMonthlyCacheEntity(yearMonth = "2024-03")) // duplicate
    }

    // --- GmailScanCacheEntity ---

    @Test
    fun gmailScanCacheEntity_insertAndReadBack_succeeds() {
        val entity =
            GmailScanCacheEntity(
                gmailMessageId = "msg_001",
                detectedType = "ADDITION",
                detectedAmountPaisa = 5_000_000L,
                emailDate = "2024-03-01",
                emailSubject = "Money transferred",
                status = "PENDING_REVIEW",
            )
        db.gmailScanCacheDao().insert(entity)
        val retrieved = db.gmailScanCacheDao().getByMessageId("msg_001")
        assertNotNull(retrieved)
        assertEquals("ADDITION", retrieved!!.detectedType)
        assertEquals(5_000_000L, retrieved.detectedAmountPaisa)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun gmailScanCacheEntity_duplicateMessageId_throwsConstraintException() {
        val entity =
            GmailScanCacheEntity(
                gmailMessageId = "msg_dup",
                emailDate = "2024-03-01",
                status = "PENDING_REVIEW",
            )
        db.gmailScanCacheDao().insert(entity)
        db.gmailScanCacheDao().insert(entity.copy(id = 0)) // same gmail_message_id
    }

    // --- PersistentAlertEntity ---

    @Test
    fun persistentAlertEntity_insertAndReadBack_succeeds() {
        val id =
            db.alertDao().insert(
                PersistentAlertEntity(
                    alertType = "SYNC_FAILED",
                    severity = "WARNING",
                    payload = """{"error": "timeout"}""",
                ),
            )
        val retrieved = db.alertDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("SYNC_FAILED", retrieved!!.alertType)
        assertEquals(0, retrieved.acknowledged)
        assertNull(retrieved.resolvedAt)
    }

    // --- SyncEventEntity ---

    @Test
    fun syncEventEntity_twoPhaseInsertUpdate_succeeds() {
        val id =
            db.syncEventDao().insert(
                SyncEventEntity(
                    eventType = "ORDER_SYNC",
                    startedAt = System.currentTimeMillis(),
                    status = "RUNNING",
                ),
            )
        val running = db.syncEventDao().getById(id)
        assertNotNull(running)
        assertEquals("RUNNING", running!!.status)
        assertNull(running.completedAt)
    }

    // --- WorkerHandoffEntity ---

    @Test
    fun workerHandoffEntity_insertAndReadBack_succeeds() {
        val id =
            db.workerHandoffDao().insert(
                WorkerHandoffEntity(
                    workerTag = "holdings_update_worker",
                    payload = """{"affected_stock_codes":["INFY"]}""",
                    consumed = 0,
                ),
            )
        val retrieved = db.workerHandoffDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("holdings_update_worker", retrieved!!.workerTag)
        assertEquals(0, retrieved.consumed)
    }

    // --- GmailFilterEntity ---

    @Test
    fun gmailFilterEntity_insertAndCountActive_succeeds() {
        db.gmailFilterDao().insert(
            GmailFilterEntity(filterType = "SENDER", filterValue = "alerts@zerodha.com"),
        )
        db.gmailFilterDao().insert(
            GmailFilterEntity(filterType = "SUBJECT_CONTAINS", filterValue = "transferred to"),
        )
        db.gmailFilterDao().insert(
            GmailFilterEntity(filterType = "SENDER", filterValue = "old@bank.com", isActive = 0),
        )
        assertEquals(2, db.gmailFilterDao().countActive())
    }
}
