package com.kitewatch.infra.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and manages the two background workers:
 *  - [OrderSyncWorker]:      daily at [DEFAULT_ORDER_SYNC_TIME] IST, network-required, with
 *                             exponential back-off for retries.
 *  - [ChargeRateSyncWorker]: weekly (no network constraint), fire-and-forget.
 *
 * Uses [ExistingPeriodicWorkPolicy.KEEP] so repeated calls on app restart are idempotent —
 * an already-enqueued periodic work is never replaced.
 */
@Singleton
class WorkSchedulerRepository
    @Inject
    constructor(
        private val workManager: WorkManager,
    ) {
        // ── Order Sync ────────────────────────────────────────────────────────────

        /**
         * Enqueues a daily [OrderSyncWorker] with an initial delay that aligns the first
         * execution to [timeOfDay] in the IST timezone.
         *
         * If a work request with [OrderSyncWorker.WORKER_TAG] is already pending or running,
         * the existing request is preserved ([ExistingPeriodicWorkPolicy.KEEP]).
         */
        fun scheduleOrderSync(timeOfDay: LocalTime = DEFAULT_ORDER_SYNC_TIME) {
            val initialDelay = initialDelayTo(timeOfDay, IST_ZONE)
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val request =
                PeriodicWorkRequestBuilder<OrderSyncWorker>(
                    repeatInterval = DAILY_PERIOD_HOURS,
                    repeatIntervalTimeUnit = TimeUnit.HOURS,
                    flexTimeInterval = FLEX_WINDOW_MINUTES,
                    flexTimeIntervalUnit = TimeUnit.MINUTES,
                ).setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
                    .addTag(OrderSyncWorker.WORKER_TAG)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                OrderSyncWorker.WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancelOrderSync() {
            workManager.cancelUniqueWork(OrderSyncWorker.WORKER_TAG)
        }

        fun isOrderSyncScheduled(): Boolean {
            val infos =
                workManager
                    .getWorkInfosForUniqueWork(OrderSyncWorker.WORKER_TAG)
                    .get()
            return infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }

        // ── Charge Rate Sync ──────────────────────────────────────────────────────

        /**
         * Enqueues a weekly [ChargeRateSyncWorker].
         * KEEP policy — idempotent across app restarts.
         */
        fun scheduleChargeRateSync() {
            val request =
                PeriodicWorkRequestBuilder<ChargeRateSyncWorker>(
                    repeatInterval = WEEKLY_PERIOD_DAYS,
                    repeatIntervalTimeUnit = TimeUnit.DAYS,
                ).addTag(ChargeRateSyncWorker.WORKER_TAG)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                ChargeRateSyncWorker.WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        internal companion object {
            val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
            val DEFAULT_ORDER_SYNC_TIME: LocalTime = LocalTime.of(16, 30)
            const val DAILY_PERIOD_HOURS = 24L
            const val FLEX_WINDOW_MINUTES = 30L
            const val BACKOFF_DELAY_MINUTES = 5L
            const val WEEKLY_PERIOD_DAYS = 7L

            /**
             * Computes the [Duration] from now until the next occurrence of [targetTime] in
             * [zone]. If the target has already passed today, schedules for tomorrow.
             */
            fun initialDelayTo(
                targetTime: LocalTime,
                zone: ZoneId,
            ): Duration {
                val now = ZonedDateTime.now(zone)
                var target = now.toLocalDate().atTime(targetTime).atZone(zone)
                if (!now.isBefore(target)) {
                    target = target.plusDays(1)
                }
                return Duration.between(now.toInstant(), target.toInstant())
            }
        }
    }
