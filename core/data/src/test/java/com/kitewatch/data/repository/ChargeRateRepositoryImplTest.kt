package com.kitewatch.data.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.kitewatch.database.dao.ChargeRateDao
import com.kitewatch.database.entity.ChargeRateEntity
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Paisa
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@Database(
    entities = [ChargeRateEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class ChargeRateTestDb : RoomDatabase() {
    abstract fun chargeRateDao(): ChargeRateDao
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChargeRateRepositoryImplTest {
    private lateinit var db: ChargeRateTestDb
    private lateinit var repository: ChargeRateRepositoryImpl

    private val standardSnapshot =
        ChargeRateSnapshot(
            brokerageDeliveryMilliBps = 0,
            sttBuyMilliBps = 10_000,
            sttSellMilliBps = 10_000,
            exchangeNseMilliBps = 297,
            exchangeBseMilliBps = 375,
            gstMilliBps = 1_800_000,
            sebiChargePerCrorePaisa = Paisa(1000L),
            stampDutyBuyMilliBps = 1_500,
            dpChargesPerScriptPaisa = Paisa(1580L),
            fetchedAt = Instant.ofEpochMilli(1_700_000_000_000L),
        )

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    ChargeRateTestDb::class.java,
                ).allowMainThreadQueries()
                .build()
        repository = ChargeRateRepositoryImpl(db.chargeRateDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `getCurrentRates returns null when no rates saved`() =
        runBlocking {
            assertNull(repository.getCurrentRates())
        }

    @Test
    fun `saveRates then getCurrentRates round-trips all fields`() =
        runBlocking {
            repository.saveRates(standardSnapshot)
            val retrieved = repository.getCurrentRates()

            assertEquals(standardSnapshot.brokerageDeliveryMilliBps, retrieved!!.brokerageDeliveryMilliBps)
            assertEquals(standardSnapshot.sttBuyMilliBps, retrieved.sttBuyMilliBps)
            assertEquals(standardSnapshot.sttSellMilliBps, retrieved.sttSellMilliBps)
            assertEquals(standardSnapshot.exchangeNseMilliBps, retrieved.exchangeNseMilliBps)
            assertEquals(standardSnapshot.exchangeBseMilliBps, retrieved.exchangeBseMilliBps)
            assertEquals(standardSnapshot.gstMilliBps, retrieved.gstMilliBps)
            assertEquals(standardSnapshot.sebiChargePerCrorePaisa, retrieved.sebiChargePerCrorePaisa)
            assertEquals(standardSnapshot.stampDutyBuyMilliBps, retrieved.stampDutyBuyMilliBps)
            assertEquals(standardSnapshot.dpChargesPerScriptPaisa, retrieved.dpChargesPerScriptPaisa)
            assertEquals(standardSnapshot.fetchedAt, retrieved.fetchedAt)
        }

    @Test
    fun `saveRates stores 9 entity rows`() =
        runBlocking {
            repository.saveRates(standardSnapshot)
            val rows = db.chargeRateDao().getLatest()
            assertEquals(9, rows.size)
        }

    @Test
    fun `second saveRates with later fetchedAt becomes the current rates`() =
        runBlocking {
            repository.saveRates(standardSnapshot)

            val updatedSnapshot =
                standardSnapshot.copy(
                    sttBuyMilliBps = 12_000,
                    fetchedAt = Instant.ofEpochMilli(1_710_000_000_000L), // later date
                )
            repository.saveRates(updatedSnapshot)

            val retrieved = repository.getCurrentRates()
            assertEquals(12_000, retrieved!!.sttBuyMilliBps)
        }
}
