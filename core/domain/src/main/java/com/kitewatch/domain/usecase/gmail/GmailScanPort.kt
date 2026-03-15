package com.kitewatch.domain.usecase.gmail

import java.time.LocalDate

/**
 * Port interface that abstracts Gmail scanning from the domain layer.
 *
 * Implemented by `:core-network`'s `GmailRemoteDataSource` adapter so that
 * `:core-domain` does not take a dependency on `:core-network`.
 */
fun interface GmailScanPort {
    /**
     * Scans Gmail for Zerodha fund-credit emails since [since].
     *
     * @param since          Only messages on or after this date.
     * @param alreadySeenIds Message IDs already stored; skipped without fetching.
     * @return List of detected fund credits.
     */
    suspend fun scanForFundCredits(
        since: LocalDate,
        alreadySeenIds: Set<String>,
    ): List<GmailFundDetection>
}

/**
 * Domain representation of a Gmail-detected fund credit.
 * Mirrors `FundDetectionResult` from `:core-network` but lives in `:core-domain`
 * so use-cases can operate without touching the network layer.
 */
data class GmailFundDetection(
    val messageId: String,
    val subject: String,
    val amountPaisa: Long,
    val date: LocalDate,
)
