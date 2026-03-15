package com.kitewatch.domain.usecase.gmail

import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.FundEntry
import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.FundRepository
import com.kitewatch.domain.usecase.AppException

/**
 * Promotes a PENDING_REVIEW Gmail detection to a confirmed [FundEntry].
 *
 * BR-14: The user must explicitly confirm a Gmail-detected fund credit before it is
 * stored as a real [FundEntry]. This use-case performs that promotion atomically:
 * 1. Fetch the cache entry for [gmailMessageId].
 * 2. Create a [FundEntry] (type = GMAIL_DETECTED) with the detected amount/date.
 * 3. Insert into the fund_entries table via [FundRepository].
 * 4. Mark the cache row CONFIRMED and link it to the newly created fund entry.
 *
 * Dismissal: call [dismiss] to mark a detection as REJECTED without creating a FundEntry.
 *
 * @param fundRepo       Port for persisting the confirmed [FundEntry].
 * @param gmailCachePort Port for reading/updating the `gmail_scan_cache` table.
 */
class ConfirmGmailEntryUseCase(
    private val fundRepo: FundRepository,
    private val gmailCachePort: GmailCachePort,
) {
    /**
     * Confirms the Gmail detection identified by [gmailMessageId].
     *
     * @return [Result] wrapping the newly created [FundEntry], or failure if:
     *  - The cache entry is not found.
     *  - The detected amount is zero or negative (BR-05).
     */
    suspend fun confirm(gmailMessageId: String): Result<FundEntry> =
        runCatching {
            val cacheEntry =
                gmailCachePort.getByMessageId(gmailMessageId)
                    ?: throw AppException(AppError.NotFoundError.GmailCacheEntryNotFound)

            val amount = Paisa(cacheEntry.amountPaisa)
            if (!amount.isPositive()) {
                throw AppException(AppError.ValidationError.NegativeAmount)
            }

            val entry =
                FundEntry(
                    entryId = 0L,
                    entryType = FundEntryType.GMAIL_DETECTED,
                    amount = amount,
                    entryDate = cacheEntry.date,
                    note = cacheEntry.subject,
                    gmailMessageId = gmailMessageId,
                )

            val insertedId = fundRepo.insertEntry(entry)
            gmailCachePort.markConfirmed(gmailMessageId, linkedFundEntryId = insertedId)
            entry.copy(entryId = insertedId)
        }

    /**
     * Dismisses the Gmail detection — marks it REJECTED so it does not surface again.
     *
     * @return [Result.success(Unit)] on success, or failure if the entry is not found.
     */
    suspend fun dismiss(gmailMessageId: String): Result<Unit> =
        runCatching {
            gmailCachePort.getByMessageId(gmailMessageId)
                ?: throw AppException(AppError.NotFoundError.GmailCacheEntryNotFound)
            gmailCachePort.markDismissed(gmailMessageId)
        }
}
