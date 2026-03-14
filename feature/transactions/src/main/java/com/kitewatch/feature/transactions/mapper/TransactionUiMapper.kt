package com.kitewatch.feature.transactions.mapper

import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.feature.transactions.model.TransactionUiModel
import com.kitewatch.ui.formatter.CurrencyFormatter
import com.kitewatch.ui.formatter.DateFormatter

internal fun Transaction.toUiModel(): TransactionUiModel =
    TransactionUiModel(
        transactionId = transactionId,
        date = DateFormatter.formatDisplay(transactionDate),
        typeLabel = type.toLabel(),
        stockCode = stockCode,
        amount = CurrencyFormatter.format(amount),
        description = description,
        isCredit = type.isCredit(),
    )

private fun TransactionType.toLabel(): String =
    when (this) {
        TransactionType.EQUITY_BUY -> "Buy"
        TransactionType.EQUITY_SELL -> "Sell"
        TransactionType.STT_CHARGE -> "STT"
        TransactionType.EXCHANGE_CHARGE -> "Exchange"
        TransactionType.SEBI_CHARGE -> "SEBI"
        TransactionType.GST_CHARGE -> "GST"
        TransactionType.STAMP_DUTY_CHARGE -> "Stamp Duty"
        TransactionType.DP_CHARGE -> "DP Charge"
        TransactionType.BROKERAGE_CHARGE -> "Brokerage"
        TransactionType.FUND_DEPOSIT -> "Fund Deposit"
        TransactionType.FUND_WITHDRAWAL -> "Withdrawal"
        TransactionType.MISC_ADJUSTMENT -> "Adjustment"
    }

private fun TransactionType.isCredit(): Boolean =
    when (this) {
        TransactionType.EQUITY_SELL,
        TransactionType.FUND_DEPOSIT,
        TransactionType.MISC_ADJUSTMENT,
        -> true

        else -> false
    }
