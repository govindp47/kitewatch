package com.kitewatch.feature.settings.di

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
}
