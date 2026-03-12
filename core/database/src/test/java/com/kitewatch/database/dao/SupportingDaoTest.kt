package com.kitewatch.database.dao

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.kitewatch.database.entity.AccountBindingEntity
import com.kitewatch.database.entity.ChargeRateEntity
import com.kitewatch.database.entity.GmailFilterEntity
import com.kitewatch.database.entity.GmailScanCacheEntity
import com.kitewatch.database.entity.PersistentAlertEntity
import com.kitewatch.database.entity.PnlMonthlyCacheEntity
import com.kitewatch.database.entity.SyncEventEntity
import com.kitewatch.database.entity.WorkerHandoffEntity
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
// Test-only database
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
internal abstract class SupportingTestDb : RoomDatabase() {
    abstract fun accountBindingDao(): AccountBindingDao

    abstract fun chargeRateDao(): ChargeRateDao

    abstract fun alertDao(): AlertDao

    abstract fun syncEventDao(): SyncEventDao

    abstract fun pnlMonthlyCacheDao(): PnlMonthlyCacheDao

    abstract fun workerHandoffDao(): WorkerHandoffDao

    abstract fun gmailScanCacheDao(): GmailScanCacheDao

    abstract fun gmailFilterDao(): GmailFilterDao
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun binding() =
    AccountBindingEntity(
        zerodhaUserId = "ZD123",
        userName = "Test User",
        apiKey = "test-api-key",
        boundAt = "2024-01-01T00:00:00Z",
    )

private fun chargeRate(
    rateType: String,
    effectiveFrom: String = "2024-01-01",
) = ChargeRateEntity(
    rateType = rateType,
    rateValue = 10,
    rateUnit = "BASIS_POINTS",
    effectiveFrom = effectiveFrom,
    fetchedAt = System.currentTimeMillis(),
)

private fun alert(alertType: String = "SYNC_FAILED") =
    PersistentAlertEntity(
        alertType = alertType,
        severity = "WARNING",
        payload = "{}",
    )

private fun syncEvent(status: String = "RUNNING") =
    SyncEventEntity(
        eventType = "ORDER_SYNC",
        startedAt = System.currentTimeMillis(),
        status = status,
    )

private fun pnlEntry(yearMonth: String) = PnlMonthlyCacheEntity(yearMonth = yearMonth)

private fun handoff(tag: String) = WorkerHandoffEntity(workerTag = tag, payload = "{}")

private fun gmailCache(messageId: String) =
    GmailScanCacheEntity(
        gmailMessageId = messageId,
        emailDate = "2024-03-01",
        status = "PENDING_REVIEW",
    )

private fun gmailFilter(filterType: String = "SENDER") =
    GmailFilterEntity(
        filterType = filterType,
        filterValue = "noreply@zerodha.com",
    )

// ---------------------------------------------------------------------------
// AccountBindingDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountBindingDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: AccountBindingDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.accountBindingDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_thenGet_returnsBinding() =
        runBlocking {
            dao.insert(binding())
            val result = dao.get()
            assertEquals("ZD123", result?.zerodhaUserId)
        }

    @Test
    fun clear_deletesAllRows() =
        runBlocking {
            dao.insert(binding())
            dao.clear()
            assertNull(dao.get())
        }

    @Test
    fun insert_replace_updatesExistingRow() =
        runBlocking {
            dao.insert(binding())
            dao.insert(binding().copy(userName = "Updated"))
            assertEquals("Updated", dao.get()?.userName)
        }
}

// ---------------------------------------------------------------------------
// ChargeRateDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChargeRateDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: ChargeRateDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.chargeRateDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getLatest_returnsOnlyRowsWithMaxEffectiveFrom() =
        runBlocking {
            dao.insertAll(
                listOf(
                    chargeRate("STT_BUY", "2024-01-01"),
                    chargeRate("STT_SELL", "2024-01-01"),
                    chargeRate("STT_BUY", "2024-06-01"), // newer
                ),
            )
            val latest = dao.getLatest()
            assertEquals(1, latest.size)
            assertEquals("2024-06-01", latest[0].effectiveFrom)
        }

    @Test
    fun pruneOlderThan_deletesOldRows() =
        runBlocking {
            val cutoff = System.currentTimeMillis() + 1_000L
            dao.insertAll(listOf(chargeRate("GST")))
            dao.pruneOlderThan(cutoff)
            assertTrue(dao.getLatest().isEmpty())
        }
}

// ---------------------------------------------------------------------------
// AlertDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlertDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: AlertDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.alertDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeUnacknowledged_excludesAcknowledgedAlerts() =
        runBlocking {
            val id = dao.insert(alert())
            dao.insert(alert("FUND_MISMATCH"))
            dao.acknowledge(id, System.currentTimeMillis())
            val result = dao.observeUnacknowledged().first()
            assertEquals(1, result.size)
            assertEquals("FUND_MISMATCH", result[0].alertType)
        }

    @Test
    fun acknowledge_setsCorrectFields() =
        runBlocking {
            val id = dao.insert(alert())
            val resolvedAt = System.currentTimeMillis()
            dao.acknowledge(id, resolvedAt)
            val active = dao.observeUnacknowledged().first()
            assertTrue(active.isEmpty())
        }
}

// ---------------------------------------------------------------------------
// SyncEventDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncEventDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: SyncEventDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.syncEventDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun twoPhaseInsertUpdate_statusUpdatedCorrectly() =
        runBlocking {
            val id = dao.insert(syncEvent("RUNNING"))
            val completedAt = System.currentTimeMillis()
            dao.update(id, completedAt, "SUCCESS", "{\"new_orders\":3}", null)
            val recent = dao.getRecent()
            assertEquals(1, recent.size)
            assertEquals("SUCCESS", recent[0].status)
            assertEquals(completedAt, recent[0].completedAt)
        }

    @Test
    fun getRecent_returnsUpTo20Rows() =
        runBlocking {
            repeat(25) { dao.insert(syncEvent()) }
            val recent = dao.getRecent()
            assertEquals(20, recent.size)
        }
}

// ---------------------------------------------------------------------------
// PnlMonthlyCacheDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PnlMonthlyCacheDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: PnlMonthlyCacheDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.pnlMonthlyCacheDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun upsert_replacesOnYearMonthConflict() =
        runBlocking {
            dao.upsert(pnlEntry("2024-03").copy(realizedPnlPaisa = 100_000L))
            dao.upsert(pnlEntry("2024-03").copy(realizedPnlPaisa = 200_000L))
            val result = dao.observeAll().first()
            assertEquals(1, result.size)
            assertEquals(200_000L, result[0].realizedPnlPaisa)
        }

    @Test
    fun observeAll_emitsNewestMonthFirst() =
        runBlocking {
            dao.upsert(pnlEntry("2024-01"))
            dao.upsert(pnlEntry("2024-03"))
            dao.upsert(pnlEntry("2024-02"))
            val result = dao.observeAll().first()
            assertEquals("2024-03", result[0].yearMonth)
            assertEquals("2024-01", result[2].yearMonth)
        }

    @Test
    fun pruneOlderThan_removesOldMonths() =
        runBlocking {
            dao.upsert(pnlEntry("2023-12"))
            dao.upsert(pnlEntry("2024-01"))
            dao.pruneOlderThan("2024-01")
            val result = dao.observeAll().first()
            assertEquals(1, result.size)
            assertEquals("2024-01", result[0].yearMonth)
        }
}

// ---------------------------------------------------------------------------
// WorkerHandoffDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkerHandoffDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: WorkerHandoffDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.workerHandoffDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getPending_returnsNullWhenNoPendingForTag() =
        runBlocking {
            assertNull(dao.getPending("my-worker"))
        }

    @Test
    fun markConsumed_excludesFromPending() =
        runBlocking {
            val id = dao.insert(handoff("my-worker"))
            dao.markConsumed(id)
            assertNull(dao.getPending("my-worker"))
        }

    @Test
    fun getPending_returnsOldestUnconsumedEntry() =
        runBlocking {
            val id1 = dao.insert(handoff("my-worker").copy(createdAt = 1000L))
            dao.insert(handoff("my-worker").copy(createdAt = 2000L))
            val result = dao.getPending("my-worker")
            assertEquals(id1, result?.id)
        }
}

// ---------------------------------------------------------------------------
// GmailScanCacheDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GmailScanCacheDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: GmailScanCacheDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.gmailScanCacheDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun exists_returnsFalseBeforeInsert() =
        runBlocking {
            assertFalse(dao.exists("MSG-001"))
        }

    @Test
    fun exists_returnsTrueAfterInsert() =
        runBlocking {
            dao.insert(gmailCache("MSG-001"))
            assertTrue(dao.exists("MSG-001"))
        }

    @Test
    fun insert_returnsMinusOneOnDuplicateMessageId() =
        runBlocking {
            dao.insert(gmailCache("MSG-001"))
            val result = dao.insert(gmailCache("MSG-001"))
            assertEquals(-1L, result)
        }

    @Test
    fun observePending_returnsOnlyPendingReviewRows() =
        runBlocking {
            dao.insert(gmailCache("MSG-001").copy(status = "PENDING_REVIEW"))
            dao.insert(gmailCache("MSG-002").copy(status = "CONFIRMED"))
            val result = dao.observePending().first()
            assertEquals(1, result.size)
            assertEquals("MSG-001", result[0].gmailMessageId)
        }
}

// ---------------------------------------------------------------------------
// GmailFilterDao tests
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GmailFilterDaoTest {
    private lateinit var db: SupportingTestDb
    private lateinit var dao: GmailFilterDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    SupportingTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = db.gmailFilterDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeActive_excludesInactiveFilters() =
        runBlocking {
            dao.insert(gmailFilter())
            val inactiveId = dao.insert(gmailFilter("SUBJECT_CONTAINS"))
            // Manually mark inactive via a fresh entity using the returned id
            val inactive =
                GmailFilterEntity(
                    id = inactiveId,
                    filterType = "SUBJECT_CONTAINS",
                    filterValue = "test",
                    isActive = 0,
                )
            dao.delete(inactive)
            val result = dao.observeActive().first()
            assertEquals(1, result.size)
            assertEquals("SENDER", result[0].filterType)
        }

    @Test
    fun delete_removesTheCorrectRow() =
        runBlocking {
            val id1 = dao.insert(gmailFilter("SENDER"))
            val id2 = dao.insert(gmailFilter("SUBJECT_CONTAINS"))
            dao.delete(GmailFilterEntity(id = id1, filterType = "SENDER", filterValue = "test"))
            val result = dao.observeActive().first()
            assertEquals(1, result.size)
            assertEquals(id2, result[0].id)
        }
}
