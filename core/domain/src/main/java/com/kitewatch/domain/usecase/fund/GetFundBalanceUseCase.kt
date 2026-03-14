package com.kitewatch.domain.usecase.fund

import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.FundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Returns a [Flow] of the current fund balance, recomputed whenever any confirmed
 * fund entry changes.
 *
 * Sign convention:
 *  - [FundEntryType.DEPOSIT], [FundEntryType.DIVIDEND], [FundEntryType.MISC_ADJUSTMENT] → +amount
 *  - [FundEntryType.WITHDRAWAL] → -amount
 *
 * Unconfirmed (Gmail-pending) entries are excluded because [FundRepository.observeEntries]
 * returns only confirmed entries (i.e., the DAO filters `is_confirmed = 1`).
 */
class GetFundBalanceUseCase(
    private val fundRepo: FundRepository,
) {
    fun execute(): Flow<Paisa> =
        fundRepo.observeEntries().map { entries ->
            entries.fold(Paisa.ZERO) { acc, entry ->
                when (entry.entryType) {
                    FundEntryType.WITHDRAWAL -> acc - entry.amount
                    else -> acc + entry.amount
                }
            }
        }
}
