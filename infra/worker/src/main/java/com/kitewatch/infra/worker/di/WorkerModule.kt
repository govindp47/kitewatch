package com.kitewatch.infra.worker.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides [WorkManager] as a singleton so it can be injected into
 * [com.kitewatch.infra.worker.WorkSchedulerRepository] without requiring
 * [WorkManager.getInstance] call-sites spread across the codebase.
 *
 * Note: [com.kitewatch.infra.worker.OrderSyncWorker] and
 * [com.kitewatch.infra.worker.ChargeRateSyncWorker] are registered with WorkManager's
 * [HiltWorkerFactory] automatically via their [@HiltWorker] + [@AssistedInject] annotations —
 * no explicit factory binding is needed here.
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)
}
