package com.kitewatch.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration test for [AppDatabase].
 *
 * This test serves as the baseline template for all future migration tests.
 * It verifies that DATABASE_VERSION = 1 opens correctly and the `orders`
 * table is readable, confirming the exported schema is consistent with the
 * Room-generated code.
 *
 * Run with: ./gradlew :core:database:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun testOpenV1Database_opensWithoutError() {
        val db = helper.createDatabase(TEST_DB_NAME, 1)
        try {
            // Insert a minimal order row via raw SQL to verify all non-null columns are present
            db.execSQL(
                """
                INSERT INTO orders
                    (zerodha_order_id, stock_code, stock_name, order_type,
                     quantity, price_paisa, total_value_paisa, trade_date,
                     exchange, source, created_at)
                VALUES
                    ('ORD-001', 'INFY', 'Infosys Limited', 'BUY',
                     10, 150000, 1500000, '2024-03-01',
                     'NSE', 'SYNC', ${System.currentTimeMillis()})
                """.trimIndent(),
            )

            val cursor = db.query("SELECT zerodha_order_id FROM orders WHERE zerodha_order_id = 'ORD-001'")
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("ORD-001", cursor.getString(0))
            cursor.close()
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "migration_test_v1.db"
    }
}
