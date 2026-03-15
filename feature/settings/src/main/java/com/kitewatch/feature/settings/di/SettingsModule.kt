package com.kitewatch.feature.settings.di

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
}
