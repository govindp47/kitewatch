package com.kitewatch.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kitewatch.database.converter.RoomTypeConverters
import com.kitewatch.database.dao.AccountBindingDao
import com.kitewatch.database.dao.AlertDao
import com.kitewatch.database.dao.ChargeRateDao
import com.kitewatch.database.dao.FundEntryDao
import com.kitewatch.database.dao.GmailFilterDao
import com.kitewatch.database.dao.GmailScanCacheDao
import com.kitewatch.database.dao.GttRecordDao
import com.kitewatch.database.dao.HoldingDao
import com.kitewatch.database.dao.OrderDao
import com.kitewatch.database.dao.OrderHoldingDao
import com.kitewatch.database.dao.PnlMonthlyCacheDao
import com.kitewatch.database.dao.SyncEventDao
import com.kitewatch.database.dao.TransactionDao
import com.kitewatch.database.dao.WorkerHandoffDao
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

/**
 * Room database for KiteWatch.
 *
 * DATABASE_VERSION = 1 — initial schema; all 14 entities.
 * fallbackToDestructiveMigration() is NOT called — every schema change
 * must be accompanied by a Migration object (per §7.2 migration rules).
 *
 * DAOs are declared as abstract functions here; implementations are provided
 * by Room's generated code. Hilt bindings for each DAO are wired in
 * DatabaseModule (T-021).
 */
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
    exportSchema = true,
)
@TypeConverters(RoomTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao

    abstract fun holdingDao(): HoldingDao

    abstract fun orderHoldingDao(): OrderHoldingDao

    abstract fun transactionDao(): TransactionDao

    abstract fun fundEntryDao(): FundEntryDao

    abstract fun gttRecordDao(): GttRecordDao

    // --- START MODIFICATION ---
    abstract fun accountBindingDao(): AccountBindingDao

    abstract fun chargeRateDao(): ChargeRateDao

    abstract fun alertDao(): AlertDao

    abstract fun syncEventDao(): SyncEventDao

    abstract fun pnlMonthlyCacheDao(): PnlMonthlyCacheDao

    abstract fun workerHandoffDao(): WorkerHandoffDao

    abstract fun gmailScanCacheDao(): GmailScanCacheDao

    abstract fun gmailFilterDao(): GmailFilterDao
    // --- END MODIFICATION ---

    companion object {
        const val DATABASE_NAME = "kitewatch.db"

        /**
         * Build the production database.
         * No fallbackToDestructiveMigration — violations are a blocking defect.
         */
        fun buildDatabase(context: Context): AppDatabase =
            Room
                .databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addTypeConverter(RoomTypeConverters())
                .build()
    }
}
