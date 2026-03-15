package com.kitewatch.feature.settings.di

import com.kitewatch.domain.repository.FundRepository
import com.kitewatch.domain.usecase.gmail.ConfirmGmailEntryUseCase
import com.kitewatch.domain.usecase.gmail.GmailCacheEntry
import com.kitewatch.domain.usecase.gmail.GmailCachePort
import com.kitewatch.domain.usecase.gmail.GmailFundDetection
import com.kitewatch.domain.usecase.gmail.GmailScanPort
import com.kitewatch.domain.usecase.gmail.ScanGmailUseCase
import com.kitewatch.domain.usecase.orders.CsvParsePort
import com.kitewatch.domain.usecase.orders.CsvParsePortResult
import com.kitewatch.domain.usecase.orders.ImportTransactionPort
import com.kitewatch.domain.usecase.orders.PreImportBackupPort
import com.kitewatch.feature.settings.BackupDriveRepository
import com.kitewatch.feature.settings.DriveBackupEntry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    fun provideBackupDriveRepository(): BackupDriveRepository =
        object : BackupDriveRepository {
            override suspend fun listBackups(): List<DriveBackupEntry> = emptyList()

            override suspend fun downloadBackupBytes(fileId: String): ByteArray =
                error("BackupDriveRepository not implemented")
        }

    @Provides
    fun provideCsvParsePort(): CsvParsePort =
        object : CsvParsePort {
            override suspend fun parse(inputStream: java.io.InputStream) = CsvParsePortResult.Success(emptyList())
        }

    @Provides
    fun providePreImportBackupPort(): PreImportBackupPort = PreImportBackupPort { _ -> Result.success(Unit) }

    @Provides
    fun provideImportTransactionPort(): ImportTransactionPort =
        object : ImportTransactionPort {
            override suspend fun runImport(
                orders: List<com.kitewatch.domain.model.Order>,
                chargesByZerodhaId: Map<String, com.kitewatch.domain.model.ChargeBreakdown>,
            ) = Unit
        }

    @Provides
    fun provideGmailScanPort(): GmailScanPort = GmailScanPort { _, _ -> emptyList() }

    @Provides
    fun provideGmailCachePort(): GmailCachePort =
        object : GmailCachePort {
            override fun observePending(): Flow<List<GmailCacheEntry>> = flowOf(emptyList())

            override suspend fun getAllMessageIds(): Set<String> = emptySet()

            override suspend fun insertPending(detection: GmailFundDetection): Long = -1L

            override suspend fun getByMessageId(messageId: String): GmailCacheEntry? = null

            override suspend fun markConfirmed(
                messageId: String,
                linkedFundEntryId: Long,
            ) = Unit

            override suspend fun markDismissed(messageId: String) = Unit
        }

    @Provides
    fun provideScanGmailUseCase(
        gmailScanPort: GmailScanPort,
        gmailCachePort: GmailCachePort,
    ): ScanGmailUseCase = ScanGmailUseCase(gmailScanPort, gmailCachePort)

    @Provides
    fun provideConfirmGmailEntryUseCase(
        fundRepository: FundRepository,
        gmailCachePort: GmailCachePort,
    ): ConfirmGmailEntryUseCase = ConfirmGmailEntryUseCase(fundRepository, gmailCachePort)
}
