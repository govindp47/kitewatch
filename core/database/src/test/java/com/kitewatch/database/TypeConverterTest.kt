package com.kitewatch.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.kitewatch.database.converter.RoomTypeConverters
import com.kitewatch.database.entity.AccountBindingEntity
import com.kitewatch.database.entity.ChargeRateEntity
import com.kitewatch.database.entity.FundEntryEntity
import com.kitewatch.database.entity.GmailFilterEntity
import com.kitewatch.database.entity.GmailScanCacheEntity
import com.kitewatch.database.entity.GttRecordEntity
import com.kitewatch.database.entity.HoldingEntity
import com.kitewatch.database.entity.OrderEntity
import com.kitewatch.database.entity.OrderHoldingEntity
import com.kitewatch.database.entity.PersistentAlertEntity
import com.kitewatch.database.entity.PnlMonthlyCacheEntity
import com.kitewatch.database.entity.SyncEventEntity
import com.kitewatch.database.entity.TransactionEntity
import com.kitewatch.database.entity.WorkerHandoffEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Minimal test-only database to verify AppDatabase opens with all 14 entities
// ---------------------------------------------------------------------------

// Test-only DB — no @TypeConverters annotation; entities use only primitive
// columns at the Room layer so no converter is needed to verify table creation.
@Database(
    entities = [
        AccountBindingEntity::class,
        OrderEntity::class,
        HoldingEntity::class,
        OrderHoldingEntity::class,
        TransactionEntity::class,
        FundEntryEntity::class,
        ChargeRateEntity::class,
        GttRecordEntity::class,
        PersistentAlertEntity::class,
        SyncEventEntity::class,
        PnlMonthlyCacheEntity::class,
        GmailScanCacheEntity::class,
        GmailFilterEntity::class,
        WorkerHandoffEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
internal abstract class AllEntitiesTestDb : RoomDatabase()

// ---------------------------------------------------------------------------
// TypeConverter unit tests (no Room required)
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TypeConverterTest {
    private val converters = RoomTypeConverters()

    // -------------------------------------------------------------------------
    // LocalDate converters
    // -------------------------------------------------------------------------

    @Test
    fun localDate_roundTrip_isoFormat() {
        val date = LocalDate.of(2024, 3, 15)
        val stored = converters.fromLocalDate(date)
        assertEquals("2024-03-15", stored)
        assertEquals(date, converters.toLocalDate(stored))
    }

    @Test
    fun localDate_null_returnsNull() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun localDate_yearBoundary_roundTrip() {
        val date = LocalDate.of(2000, 1, 1)
        assertEquals(date, converters.toLocalDate(converters.fromLocalDate(date)))
    }

    @Test
    fun localDate_endOfYear_roundTrip() {
        val date = LocalDate.of(2024, 12, 31)
        assertEquals(date, converters.toLocalDate(converters.fromLocalDate(date)))
    }

    // -------------------------------------------------------------------------
    // Instant converters
    // -------------------------------------------------------------------------

    @Test
    fun instant_roundTrip_epochMillis() {
        val instant = Instant.ofEpochMilli(1_700_000_000_000L)
        val stored = converters.fromInstant(instant)
        assertEquals(1_700_000_000_000L, stored)
        assertEquals(instant, converters.toInstant(stored))
    }

    @Test
    fun instant_null_returnsNull() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
    }

    @Test
    fun instant_epochZero_roundTrip() {
        val instant = Instant.ofEpochMilli(0L)
        assertEquals(instant, converters.toInstant(converters.fromInstant(instant)))
    }

    @Test
    fun instant_negativeEpoch_roundTrip() {
        val instant = Instant.ofEpochMilli(-1_000L)
        assertEquals(instant, converters.toInstant(converters.fromInstant(instant)))
    }

    // -------------------------------------------------------------------------
    // AppDatabase opens with all 14 entities — no IllegalStateException
    // -------------------------------------------------------------------------

    @Test
    fun appDatabase_openWithAll14Entities_noException() {
        val db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AllEntitiesTestDb::class.java,
                ).allowMainThreadQueries()
                .build()

        // Opening the DB without crashing is the acceptance criterion.
        // Verify all expected tables exist via raw query.
        val tableNames = mutableListOf<String>()

        @Suppress("MaxLineLength")
        val sql =
            "SELECT name FROM sqlite_master WHERE type='table'" +
                " AND name NOT LIKE 'sqlite_%'" +
                " AND name NOT LIKE 'room_%'" +
                " AND name NOT LIKE 'android_%'"
        val cursor = db.openHelper.readableDatabase.query(sql)
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()
        db.close()

        val expectedTables =
            setOf(
                "account_binding",
                "orders",
                "holdings",
                "order_holdings",
                "transactions",
                "fund_entries",
                "charge_rates",
                "gtt_records",
                "persistent_alerts",
                "sync_event_log",
                "pnl_monthly_cache",
                "gmail_scan_cache",
                "gmail_filters",
                "worker_handoff",
            )
        assertEquals("Expected 14 tables, found: $tableNames", 14, tableNames.size)
        assert(tableNames.toSet().containsAll(expectedTables)) {
            "Missing tables: ${expectedTables - tableNames.toSet()}"
        }
    }
}
