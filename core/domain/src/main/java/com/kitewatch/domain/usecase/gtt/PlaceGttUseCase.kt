package com.kitewatch.domain.usecase.gtt

import com.kitewatch.domain.engine.GttAction
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.AlertSeverity
import com.kitewatch.domain.repository.AlertType
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.PersistentAlert
import com.kitewatch.domain.usecase.AppException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant

/**
 * Outcome of a [PlaceGttUseCase.execute] call.
 *
 * @param succeeded Actions that completed successfully (API call confirmed + local state updated).
 * @param failed    Actions that failed after all retry attempts. Includes the terminal error.
 * @param flagged   [GttAction.FlagManualOverride] actions — no API call made; local state updated
 *                  and alert inserted.
 */
data class GttPlacementResult(
    val succeeded: List<GttAction>,
    val failed: List<Pair<GttAction, Throwable>>,
    val flagged: List<GttAction.FlagManualOverride>,
)

/**
 * Executes a list of [GttAction] items produced by [com.kitewatch.domain.engine.GttAutomationEngine]
 * against the Kite Connect GTT API.
 *
 * Responsibilities:
 *  - [GttAction.CreateGtt]: POST to Kite, insert [GttRecord] as ACTIVE on success; insert
 *    PENDING_CREATION on retry exhaustion.
 *  - [GttAction.UpdateGtt]: PUT to Kite, update local status; handle 404 by archiving local
 *    record and emitting a [AlertType.GTT_VERIFICATION_FAILED] alert.
 *  - [GttAction.ArchiveGtt]: DELETE from Kite, archive local record.
 *  - [GttAction.FlagManualOverride]: zero API calls; update local status and emit
 *    [AlertType.GTT_MANUAL_OVERRIDE] alert.
 *  - [GttAction.NoAction]: ignored.
 *
 * Retry policy for Create/Update: max [MAX_ATTEMPTS] attempts with [RETRY_BASE_DELAY_MS] × attempt
 * delay between failures (exponential backoff).
 *
 * Zero Android dependencies.
 */
class PlaceGttUseCase(
    private val kiteConnectRepo: KiteConnectRepository,
    private val gttRepo: GttRepository,
    private val alertRepo: AlertRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun execute(actions: List<GttAction>): GttPlacementResult {
        // Pre-load active GTT records so UpdateGtt / ArchiveGtt / FlagManualOverride can
        // resolve zerodhaGttId and stockCode from the local DB ID carried in GttAction.
        val activeGtts: Map<Long, GttRecord> = gttRepo.observeActive().first().associateBy { it.gttId }

        val succeeded = mutableListOf<GttAction>()
        val failed = mutableListOf<Pair<GttAction, Throwable>>()
        val flagged = mutableListOf<GttAction.FlagManualOverride>()

        for (action in actions) {
            when (action) {
                is GttAction.NoAction -> Unit
                is GttAction.CreateGtt -> handleCreate(action, succeeded, failed)
                is GttAction.UpdateGtt -> handleUpdate(action, activeGtts, succeeded, failed)
                is GttAction.ArchiveGtt -> handleArchive(action, activeGtts, succeeded, failed)
                is GttAction.FlagManualOverride -> handleFlag(action, activeGtts, flagged)
            }
        }

        return GttPlacementResult(succeeded, failed, flagged)
    }

    // ── CreateGtt ─────────────────────────────────────────────────────────────

    private suspend fun handleCreate(
        action: GttAction.CreateGtt,
        succeeded: MutableList<GttAction>,
        failed: MutableList<Pair<GttAction, Throwable>>,
    ) {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val result = kiteConnectRepo.createGtt(action.stockCode, action.quantity, action.targetPrice)
            if (result.isSuccess) {
                val zerodhaGttId = result.getOrThrow()
                gttRepo.upsert(
                    GttRecord(
                        gttId = 0L,
                        zerodhaGttId = zerodhaGttId,
                        stockCode = action.stockCode,
                        triggerPrice = action.targetPrice,
                        quantity = action.quantity,
                        status = GttStatus.ACTIVE,
                        isAppManaged = true,
                        lastSyncedAt = Instant.now(clock),
                    ),
                )
                succeeded.add(action)
                return
            }
            lastError = result.exceptionOrNull()
            if (attempt < MAX_ATTEMPTS) delay(RETRY_BASE_DELAY_MS * attempt)
        }
        // Retry exhausted — store PENDING_CREATION for next sync cycle.
        gttRepo.upsert(
            GttRecord(
                gttId = 0L,
                zerodhaGttId = null,
                stockCode = action.stockCode,
                triggerPrice = action.targetPrice,
                quantity = action.quantity,
                status = GttStatus.PENDING_CREATION,
                isAppManaged = true,
                lastSyncedAt = null,
            ),
        )
        failed.add(action to (lastError ?: Exception("GTT creation failed after $MAX_ATTEMPTS attempts")))
    }

    // ── UpdateGtt ─────────────────────────────────────────────────────────────

    private suspend fun handleUpdate(
        action: GttAction.UpdateGtt,
        activeGtts: Map<Long, GttRecord>,
        succeeded: MutableList<GttAction>,
        failed: MutableList<Pair<GttAction, Throwable>>,
    ) {
        val record = activeGtts[action.gttId]
        val zerodhaGttId = record?.zerodhaGttId
        val now = Instant.now(clock)

        if (zerodhaGttId == null) {
            // No Zerodha ID — cannot call API; defer to next cycle.
            gttRepo.updateStatus(action.gttId, GttStatus.PENDING_UPDATE, now)
            failed.add(action to Exception("zerodhaGttId missing for gttId=${action.gttId}"))
            return
        }

        var completed = false
        var lastError: Throwable? = null

        for (attempt in 1..MAX_ATTEMPTS) {
            val result = kiteConnectRepo.updateGtt(zerodhaGttId, action.newQuantity, action.newTargetPrice)
            when {
                result.isSuccess -> {
                    gttRepo.updateStatus(action.gttId, GttStatus.ACTIVE, now)
                    succeeded.add(action)
                    completed = true
                }
                isHttpNotFound(result.exceptionOrNull()) -> {
                    // GTT no longer exists on Zerodha — archive locally and alert.
                    gttRepo.archive(action.gttId, now)
                    alertRepo.insert(
                        PersistentAlert(
                            alertType = AlertType.GTT_VERIFICATION_FAILED,
                            severity = AlertSeverity.WARNING,
                            payload =
                                "GTT $zerodhaGttId not found on Zerodha (404)" +
                                    record?.stockCode?.let { " — stock: $it" }.orEmpty(),
                            createdAt = now,
                        ),
                    )
                    succeeded.add(action)
                    completed = true
                }
                else -> {
                    lastError = result.exceptionOrNull()
                    if (attempt < MAX_ATTEMPTS) delay(RETRY_BASE_DELAY_MS * attempt)
                }
            }

            if (completed) {
                break
            }
        }

        if (!completed) {
            gttRepo.updateStatus(action.gttId, GttStatus.PENDING_UPDATE, now)
            failed.add(action to (lastError ?: Exception("GTT update failed after $MAX_ATTEMPTS attempts")))
        }
    }

    // ── ArchiveGtt ────────────────────────────────────────────────────────────

    private suspend fun handleArchive(
        action: GttAction.ArchiveGtt,
        activeGtts: Map<Long, GttRecord>,
        succeeded: MutableList<GttAction>,
        failed: MutableList<Pair<GttAction, Throwable>>,
    ) {
        val record = activeGtts[action.gttId]
        val zerodhaGttId = record?.zerodhaGttId
        val now = Instant.now(clock)

        if (zerodhaGttId != null) {
            val result = kiteConnectRepo.deleteGtt(zerodhaGttId)
            if (result.isFailure && !isHttpNotFound(result.exceptionOrNull())) {
                failed.add(action to (result.exceptionOrNull() ?: Exception("GTT delete failed")))
                return
            }
            // isSuccess OR 404 (already gone on Zerodha) → archive locally
        }
        gttRepo.archive(action.gttId, now)
        succeeded.add(action)
    }

    // ── FlagManualOverride ────────────────────────────────────────────────────

    private suspend fun handleFlag(
        action: GttAction.FlagManualOverride,
        activeGtts: Map<Long, GttRecord>,
        flagged: MutableList<GttAction.FlagManualOverride>,
    ) {
        val record = activeGtts[action.gttId]
        val now = Instant.now(clock)

        gttRepo.updateStatus(action.gttId, GttStatus.MANUAL_OVERRIDE_DETECTED, now)
        alertRepo.insert(
            PersistentAlert(
                alertType = AlertType.GTT_MANUAL_OVERRIDE,
                severity = AlertSeverity.WARNING,
                payload =
                    buildString {
                        append("Manual override detected")
                        record?.stockCode?.let { append(" for $it") }
                        append(": app_target=${action.appTargetPrice.value}")
                        append(", zerodha_actual=${action.zerodhaActualPrice.value}")
                    },
                createdAt = now,
            ),
        )
        flagged.add(action)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isHttpNotFound(error: Throwable?): Boolean =
        error is AppException &&
            (error.error as? AppError.NetworkError.HttpError)?.code == HTTP_NOT_FOUND

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 2_000L
        const val HTTP_NOT_FOUND = 404
    }
}
