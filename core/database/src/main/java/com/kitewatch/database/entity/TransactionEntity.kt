package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the `transactions` table.
 * Append-only ledger — no updated_at column by design (INV-10).
 * Monetary values stored as Long (paisa). Dates stored as ISO-8601 TEXT.
 * Sign convention: positive = inflow, negative = outflow.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["type"]),
        Index(value = ["transaction_date"]),
        Index(value = ["stock_code"]),
        Index(value = ["reference_id"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * One of: FUND_ADDITION, FUND_WITHDRAWAL, EQUITY_BUY, EQUITY_SELL,
     * BROKERAGE_CHARGE, STT_CHARGE, EXCHANGE_CHARGE, GST_CHARGE,
     * SEBI_CHARGE, STAMP_DUTY_CHARGE, MISC_ADJUSTMENT, DP_CHARGE
     */
    val type: String,
    /** Optional sub-classification, e.g. 'AUTO_RECONCILIATION' */
    @ColumnInfo(name = "sub_type")
    val subType: String? = null,
    /** Polymorphic FK: zerodha_order_id, fund_entry id, or reconciliation id */
    @ColumnInfo(name = "reference_id")
    val referenceId: String? = null,
    /** One of: ORDER, FUND_ENTRY, RECONCILIATION — or null */
    @ColumnInfo(name = "reference_type")
    val referenceType: String? = null,
    /** Null for fund/adjustment transactions */
    @ColumnInfo(name = "stock_code")
    val stockCode: String? = null,
    /** Positive = inflow, negative = outflow (paisa) */
    @ColumnInfo(name = "amount_paisa")
    val amountPaisa: Long,
    /**
     * Running cash balance AFTER this transaction (paisa).
     * Populated for all transaction types that affect the fund balance.
     */
    @ColumnInfo(name = "running_fund_balance_paisa")
    val runningFundBalancePaisa: Long? = null,
    /** Human-readable description, e.g. "Buy 10 INFY @ ₹1,500.00" */
    val description: String = "",
    /** ISO-8601 date: YYYY-MM-DD */
    @ColumnInfo(name = "transaction_date")
    val transactionDate: String,
    /** One of: SYSTEM, MANUAL, GMAIL, CSV_IMPORT, RECONCILIATION */
    val source: String = "SYSTEM",
    /** Epoch milliseconds UTC */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    // NO updated_at — transactions are immutable after insertion (INV-10)
)
