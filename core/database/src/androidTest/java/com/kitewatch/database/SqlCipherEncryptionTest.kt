package com.kitewatch.database

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitewatch.database.entity.OrderEntity
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.time.LocalDate

/**
 * Verifies that:
 * 1. The database is readable/writable through SQLCipher-encrypted Room.
 * 2. The database file cannot be opened without the correct passphrase.
 *
 * Uses a fixed test passphrase — production code uses a Keystore-backed passphrase
 * from [com.kitewatch.infra.auth.MasterKeyProvider.databasePassphrase].
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherEncryptionTest {
    private lateinit var db: AppDatabase
    private lateinit var testPassphrase: ByteArray

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SQLiteDatabase.loadLibs(context)

        testPassphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val factory = SupportFactory(testPassphrase)

        db =
            Room
                .databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
                .openHelperFactory(factory)
                .addTypeConverter(RoomTypeConverters())
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
        ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun encryptedDatabase_roundTrip_succeeds() {
        val order =
            OrderEntity(
                id = 0,
                zerodhaOrderId = "ZOD001",
                stockCode = "INFY",
                stockName = "Infosys Ltd",
                orderType = "BUY",
                quantity = 10,
                pricePaisa = 150_000_00L,
                totalValuePaisa = 1_500_000_00L,
                tradeDate = LocalDate.of(2024, 1, 15).toString(),
                exchange = "NSE",
                settlementId = null,
                source = "SYNC",
                createdAt = System.currentTimeMillis(),
            )

        val insertedId = db.orderDao().insert(order)
        assertTrue("Inserted row must have a valid ID", insertedId > 0)

        val fetched = db.orderDao().getAll()
        assertEquals("Exactly one order must be returned", 1, fetched.size)
        assertEquals("Zerodha order ID must match", "ZOD001", fetched[0].zerodhaOrderId)
        assertEquals("Stock code must match", "INFY", fetched[0].stockCode)
    }

    @Test
    fun encryptedDatabase_openWithWrongPassphrase_throws() {
        // Write at least one row so the file exists on disk.
        val order =
            OrderEntity(
                id = 0,
                zerodhaOrderId = "ZOD002",
                stockCode = "TCS",
                stockName = "Tata Consultancy Services",
                orderType = "BUY",
                quantity = 5,
                pricePaisa = 350_000_00L,
                totalValuePaisa = 1_750_000_00L,
                tradeDate = LocalDate.of(2024, 2, 1).toString(),
                exchange = "NSE",
                settlementId = null,
                source = "SYNC",
                createdAt = System.currentTimeMillis(),
            )
        db.orderDao().insert(order)
        db.close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val wrongPassphrase = ByteArray(32) { 0x42 }
        val wrongFactory = SupportFactory(wrongPassphrase)

        val wrongDb =
            Room
                .databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
                .openHelperFactory(wrongFactory)
                .addTypeConverter(RoomTypeConverters())
                .allowMainThreadQueries()
                .build()

        assertThrows(
            "Opening with a wrong passphrase must throw",
            SQLiteException::class.java,
        ) {
            wrongDb.orderDao().getAll()
        }
        wrongDb.close()
    }

    companion object {
        private const val TEST_DB_NAME = "sqlcipher_test.db"
    }
}
