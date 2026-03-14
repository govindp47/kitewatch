package com.kitewatch.data.mapper

import com.kitewatch.database.entity.TransactionEntity
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionSource
import com.kitewatch.domain.model.TransactionType
import java.time.LocalDate

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        transactionId = id,
        type = TransactionType.valueOf(type),
        referenceId = referenceId,
        stockCode = stockCode,
        amount = Paisa(amountPaisa),
        transactionDate = LocalDate.parse(transactionDate),
        description = description,
        source = entitySourceToDomain(source),
    )

fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        id = transactionId,
        type = type.name,
        referenceId = referenceId,
        stockCode = stockCode,
        amountPaisa = amount.value,
        transactionDate = transactionDate.toString(),
        description = description,
        source = domainSourceToEntity(source),
    )

private fun entitySourceToDomain(source: String): TransactionSource =
    when (source) {
        "SYSTEM" -> TransactionSource.SYNC
        "MANUAL", "RECONCILIATION" -> TransactionSource.MANUAL
        "GMAIL" -> TransactionSource.GMAIL_SCAN
        "CSV_IMPORT" -> TransactionSource.CSV_IMPORT
        else -> TransactionSource.SYNC
    }

private fun domainSourceToEntity(source: TransactionSource): String =
    when (source) {
        TransactionSource.SYNC -> "SYSTEM"
        TransactionSource.MANUAL -> "MANUAL"
        TransactionSource.GMAIL_SCAN -> "GMAIL"
        TransactionSource.CSV_IMPORT -> "CSV_IMPORT"
    }
