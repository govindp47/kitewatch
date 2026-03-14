package com.kitewatch.infra.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.SyncResult
import com.kitewatch.domain.repository.SyncEventRepository
import com.kitewatch.domain.usecase.AppException
import com.kitewatch.domain.usecase.SyncOrdersUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/**
 * Daily background worker that drives the full order-sync cycle via [SyncOrdersUseCase].
 *
 * Retry policy (configured by [WorkSchedulerRepository]):
 *  - Network errors   → [Result.retry] (WorkManager handles back-off per the work request)
 *  - Holdings mismatch / domain errors → [Result.failure] (no retry — requires manual intervention)
 *  - Success or Skipped/NoNewOrders   → [Result.success]
 *
 * Each run is bracketed by [SyncEventRepository.beginEvent] / [SyncEventRepository.finishEvent]
 * so the audit log always has a terminal status — even when this worker is retried by WorkManager.
 */
@HiltWorker
class OrderSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val syncOrdersUseCase: SyncOrdersUseCase,
        private val syncEventRepo: SyncEventRepository,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val startedAt = Instant.now()
            val eventId = syncEventRepo.beginEvent(EVENT_TYPE, startedAt, WORKER_TAG)

            val syncResult = syncOrdersUseCase.execute()

            return if (syncResult.isSuccess) {
                val details =
                    when (val r = syncResult.getOrThrow()) {
                        is SyncResult.Success -> "orders=${r.newOrderCount} gtts=${r.updatedGttCount}"
                        SyncResult.NoNewOrders -> "no_new_orders"
                        is SyncResult.Skipped -> "skipped: ${r.reason}"
                        is SyncResult.Partial -> "partial: ok=${r.succeeded} fail=${r.failed}"
                    }
                syncEventRepo.finishEvent(eventId, Instant.now(), STATUS_SUCCESS, details)
                Result.success()
            } else {
                val error = syncResult.exceptionOrNull()
                val appError = (error as? AppException)?.error
                if (appError is AppError.NetworkError) {
                    syncEventRepo.finishEvent(
                        id = eventId,
                        completedAt = Instant.now(),
                        status = STATUS_RETRYING,
                        errorMessage = error.message,
                    )
                    Result.retry()
                } else {
                    syncEventRepo.finishEvent(
                        id = eventId,
                        completedAt = Instant.now(),
                        status = STATUS_FAILED,
                        errorMessage = error?.message,
                    )
                    Result.failure()
                }
            }
        }

        companion object {
            const val WORKER_TAG = "order_sync"
            const val EVENT_TYPE = "ORDER_SYNC"
            private const val STATUS_SUCCESS = "SUCCESS"
            private const val STATUS_RETRYING = "RETRYING"
            private const val STATUS_FAILED = "FAILED"
        }
    }
