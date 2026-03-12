package com.kitewatch.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    // DAOs — stubbed here; full implementations in T-018 through T-020.
    // Each function name matches the naming convention in DatabaseModule (T-021).

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
