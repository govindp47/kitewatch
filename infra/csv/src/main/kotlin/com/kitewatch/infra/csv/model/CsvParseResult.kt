package com.kitewatch.infra.csv.model

import com.kitewatch.domain.model.Order

/**
 * Outcome of parsing and validating a CSV import file.
 *
 * Semantics are all-or-nothing: either every row is valid ([Success]) or the entire
 * parse is rejected with a complete list of every error found ([ValidationFailure]).
 */
sealed class CsvParseResult {
    /**
     * Every row in the file was valid and passed all business-level checks.
     * [orders] contains only delivery equity (CNC/EQ) rows; non-delivery rows are
     * silently excluded and do NOT appear as errors.
     */
    data class Success(
        val orders: List<Order>,
    ) : CsvParseResult()

    /**
     * One or more rows failed validation. [errors] lists every problem found
     * (not just the first). All rows were inspected before this is returned.
     */
    data class ValidationFailure(
        val errors: List<CsvRowError>,
    ) : CsvParseResult()
}

/**
 * A single row-level validation error.
 *
 * @param rowNumber 1-based row number in the source file (header = row 0; first data row = 1).
 * @param field     The column name that caused the failure, or "row" for row-level issues.
 * @param message   Human-readable description of why the value is invalid.
 */
data class CsvRowError(
    val rowNumber: Int,
    val field: String,
    val message: String,
)

/**
 * The three CSV formats accepted by the import pipeline.
 */
enum class CsvFormat {
    /** Kite Trade Book export (tradebook-*.csv). */
    KITE_TRADE_BOOK,

    /** Kite web console order history export. */
    KITE_ORDERS,

    /** Custom KiteWatch CSV format. */
    KITEWATCH_CUSTOM,
}
