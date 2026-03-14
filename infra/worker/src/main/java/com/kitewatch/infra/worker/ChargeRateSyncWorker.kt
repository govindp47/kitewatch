package com.kitewatch.infra.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.ChargeRateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/**
 * Weekly worker that seeds default Zerodha equity delivery charge rates if none are stored.
 *
 * In future phases this worker will fetch live rates from a remote config source.
 * For now it ensures the local [ChargeRateRepository] is never empty so that
 * [com.kitewatch.domain.usecase.SyncOrdersUseCase] can always compute charges.
 *
 * Always returns [Result.success] — charge rate absence is non-critical and should
 * not block or retry the daily sync.
 */
@HiltWorker
class ChargeRateSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val chargeRateRepo: ChargeRateRepository,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            runCatching {
                if (chargeRateRepo.getCurrentRates() == null) {
                    chargeRateRepo.saveRates(defaultRates())
                }
            }
            // Non-critical — swallow any error and report success.
            return Result.success()
        }

        private fun defaultRates(): ChargeRateSnapshot =
            ChargeRateSnapshot(
                brokerageDeliveryMilliBps = 0,
                sttBuyMilliBps = 10_000,
                sttSellMilliBps = 10_000,
                exchangeNseMilliBps = 297,
                exchangeBseMilliBps = 375,
                gstMilliBps = 1_800_000,
                sebiChargePerCrorePaisa = Paisa(1_000L), // ₹10 per crore
                stampDutyBuyMilliBps = 1_500,
                dpChargesPerScriptPaisa = Paisa(1_580L), // ₹15.80 per script per sell day
                fetchedAt = Instant.now(),
            )

        companion object {
            const val WORKER_TAG = "charge_rate_sync"
        }
    }
