package com.kitewatch.data.mapper

import com.kitewatch.database.entity.FundEntryEntity
import com.kitewatch.domain.model.FundEntry
import com.kitewatch.domain.model.FundEntryType
import com.kitewatch.domain.model.Paisa
import java.time.LocalDate

fun FundEntryEntity.toDomain(): FundEntry =
    FundEntry(
        entryId = id,
        entryType = entityEntryTypeToDomain(entryType),
        amount = Paisa(amountPaisa),
        entryDate = LocalDate.parse(entryDate),
        note = note,
        gmailMessageId = gmailMessageId,
    )

fun FundEntry.toEntity(): FundEntryEntity =
    FundEntryEntity(
        id = entryId,
        entryType = domainEntryTypeToEntity(entryType),
        amountPaisa = amount.value,
        entryDate = entryDate.toString(),
        note = note,
        gmailMessageId = gmailMessageId,
        isConfirmed = 1,
    )

private fun entityEntryTypeToDomain(type: String): FundEntryType =
    when (type) {
        "ADDITION" -> FundEntryType.DEPOSIT
        "WITHDRAWAL" -> FundEntryType.WITHDRAWAL
        "DIVIDEND" -> FundEntryType.DIVIDEND
        else -> FundEntryType.MISC_ADJUSTMENT
    }

private fun domainEntryTypeToEntity(type: FundEntryType): String =
    when (type) {
        FundEntryType.DEPOSIT -> "ADDITION"
        FundEntryType.WITHDRAWAL -> "WITHDRAWAL"
        FundEntryType.DIVIDEND -> "DIVIDEND"
        FundEntryType.MISC_ADJUSTMENT -> "MISC_ADJUSTMENT"
    }
