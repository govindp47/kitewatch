package com.kitewatch.domain.usecase.fund

import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.FundEntry
import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.FundRepository
import com.kitewatch.domain.usecase.AppException
import java.time.LocalDate

/**
 * Records a manual fund entry (deposit, withdrawal, dividend, or adjustment).
 *
 * BR-05: amount must be > 0. Returns [AppError.ValidationError.NegativeAmount] if not.
 */
class AddFundEntryUseCase(
    private val fundRepo: FundRepository,
) {
    suspend fun execute(
        amount: Paisa,
        date: LocalDate,
        note: String?,
        entryType: FundEntryType,
    ): Result<FundEntry> {
        if (!amount.isPositive()) {
            return Result.failure(AppException(AppError.ValidationError.NegativeAmount))
        }

        val entry =
            FundEntry(
                entryId = 0L,
                entryType = entryType,
                amount = amount,
                entryDate = date,
                note = note,
                gmailMessageId = null,
            )

        val insertedId = fundRepo.insertEntry(entry)
        return Result.success(entry.copy(entryId = insertedId))
    }
}
