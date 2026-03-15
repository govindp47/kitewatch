package com.kitewatch.database.di

import android.content.Context
import com.kitewatch.database.AppDatabase
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
import com.kitewatch.infra.auth.MasterKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // --- START MODIFICATION ---
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
        masterKeyProvider: MasterKeyProvider,
    ): AppDatabase = AppDatabase.buildDatabase(context, masterKeyProvider.databasePassphrase)
    // --- END MODIFICATION ---

    @Provides
    fun provideOrderDao(db: AppDatabase): OrderDao = db.orderDao()

    @Provides
    fun provideHoldingDao(db: AppDatabase): HoldingDao = db.holdingDao()

    @Provides
    fun provideOrderHoldingDao(db: AppDatabase): OrderHoldingDao = db.orderHoldingDao()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideFundEntryDao(db: AppDatabase): FundEntryDao = db.fundEntryDao()

    @Provides
    fun provideGttRecordDao(db: AppDatabase): GttRecordDao = db.gttRecordDao()

    @Provides
    fun provideAccountBindingDao(db: AppDatabase): AccountBindingDao = db.accountBindingDao()

    @Provides
    fun provideChargeRateDao(db: AppDatabase): ChargeRateDao = db.chargeRateDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()

    @Provides
    fun provideSyncEventDao(db: AppDatabase): SyncEventDao = db.syncEventDao()

    @Provides
    fun providePnlMonthlyCacheDao(db: AppDatabase): PnlMonthlyCacheDao = db.pnlMonthlyCacheDao()

    @Provides
    fun provideWorkerHandoffDao(db: AppDatabase): WorkerHandoffDao = db.workerHandoffDao()

    @Provides
    fun provideGmailScanCacheDao(db: AppDatabase): GmailScanCacheDao = db.gmailScanCacheDao()

    @Provides
    fun provideGmailFilterDao(db: AppDatabase): GmailFilterDao = db.gmailFilterDao()
}
