package com.kitewatch.domain.usecase.gmail

import java.time.LocalDate

/**
 * Orchestrates a Gmail scan for Zerodha fund-credit emails and persists new detections
 * as PENDING_REVIEW cache entries.
 *
 * BR-14: Detected entries must await explicit user confirmation before becoming a FundEntry.
 *
 * Idempotency: `GmailCachePort.insertPending` uses IGNORE conflict strategy, so
 * re-running the scan for an already-seen message ID is a safe no-op.
 *
 * @param gmailScanPort  Adapter that calls the Gmail REST API.
 * @param gmailCachePort Adapter that reads/writes the `gmail_scan_cache` table.
 */
class ScanGmailUseCase(
    private val gmailScanPort: GmailScanPort,
    private val gmailCachePort: GmailCachePort,
) {
    /**
     * Runs the Gmail scan and stores new detections.
     *
     * @param lookbackDays How many days back to scan (default 90).
     * @return [Result] wrapping the count of *new* pending detections inserted this run.
     */
    suspend fun execute(lookbackDays: Long = LOOKBACK_DAYS): Result<Int> =
        runCatching {
            val since = LocalDate.now().minusDays(lookbackDays)
            val alreadySeen = gmailCachePort.getAllMessageIds()

            val detections =
                gmailScanPort.scanForFundCredits(
                    since = since,
                    alreadySeenIds = alreadySeen,
                )

            var newCount = 0
            for (detection in detections) {
                val rowId = gmailCachePort.insertPending(detection)
                if (rowId != -1L) newCount++
            }
            newCount
        }

    companion object {
        private const val LOOKBACK_DAYS = 90L
    }
}
