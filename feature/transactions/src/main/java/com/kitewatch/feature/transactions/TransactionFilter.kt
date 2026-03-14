package com.kitewatch.feature.transactions

import com.kitewatch.domain.model.TransactionType

/**
 * Filter options for the Transactions screen.
 * [ALL] maps to null (no filter), others map to specific [TransactionType] values.
 */
enum class TransactionFilter(
    val type: TransactionType?,
) {
    ALL(null),
    BUY(TransactionType.EQUITY_BUY),
    SELL(TransactionType.EQUITY_SELL),
    FUND_CREDIT(TransactionType.FUND_DEPOSIT),
    FUND_DEBIT(TransactionType.FUND_WITHDRAWAL),
}
