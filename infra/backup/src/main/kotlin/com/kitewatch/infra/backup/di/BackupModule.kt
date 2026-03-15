package com.kitewatch.infra.backup.di

import androidx.room.withTransaction
import com.kitewatch.database.AppDatabase
import com.kitewatch.infra.backup.BackupHistoryRecorder
import com.kitewatch.infra.backup.NoOpBackupHistoryRecorder
import com.kitewatch.infra.backup.PreferencesExporter
import com.kitewatch.infra.backup.PreferencesRestorer
import com.kitewatch.infra.backup.datasource.GoogleDriveRemoteDataSource
import com.kitewatch.infra.backup.model.UserPreferences
import com.kitewatch.infra.backup.usecase.DatabaseTransactionRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object BackupModule {
    @Provides
    fun provideDatabaseTransactionRunner(db: AppDatabase): DatabaseTransactionRunner =
        DatabaseTransactionRunner { block -> db.withTransaction(block) }

    @Provides
    fun provideBackupHistoryRecorder(): BackupHistoryRecorder = NoOpBackupHistoryRecorder()

    @Provides
    fun providePreferencesExporter(): PreferencesExporter =
        object : PreferencesExporter {
            override suspend fun exportAll(): UserPreferences = UserPreferences()
        }

    @Provides
    fun providePreferencesRestorer(): PreferencesRestorer =
        object : PreferencesRestorer {
            override suspend fun importAll(preferences: UserPreferences) = Unit
        }

    @Provides
    fun provideGoogleDriveRemoteDataSource(): GoogleDriveRemoteDataSource =
        object : GoogleDriveRemoteDataSource {
            override suspend fun upload(
                fileName: String,
                fileBytes: ByteArray,
            ): String = error("GoogleDriveRemoteDataSource not implemented")

            override suspend fun downloadBackup(fileId: String): ByteArray =
                error("GoogleDriveRemoteDataSource not implemented")
        }
}
