package com.kitewatch.infra.csv

import com.kitewatch.domain.model.Order
import com.kitewatch.infra.csv.model.CsvRowError
import java.time.LocalDate

/**
 * Validates business-level rules on a list of [Order] objects produced by [CsvParser].
 *
 * This class is intentionally separated from [CsvParser] (which validates format/field-level
 * rules) so that business constraints can be tested independently.
 *
 * Has **zero database dependencies**: the set of existing order IDs is provided by the
 * caller (typically the use case layer, which queries the database before calling the validator).
 */
object CsvValidator {
    /**
     * Validates [orders] against business-level rules.
     *
     * @param orders           The parsed orders to validate.
     * @param existingOrderIds Set of `zerodha_order_id` values already stored in the
     *                         database. Used for duplicate detection. No DB access here.
     * @param today            The reference date used to detect future-dated orders.
     *                         Defaults to [LocalDate.now]. Injected for testability.
     *
     * @return An empty list if all orders pass; otherwise every error found (not fail-fast).
     */
    fun validate(
        orders: List<Order>,
        existingOrderIds: Set<String>,
        today: LocalDate = LocalDate.now(),
    ): List<CsvRowError> {
        val errors = mutableListOf<CsvRowError>()

        // Track order IDs seen within this import batch to catch intra-file duplicates
        val seenInBatch = mutableSetOf<String>()

        orders.forEachIndexed { index, order ->
            val rowNumber = index + 1 // 1-based; matches the row number in CsvParseResult

            // Rule 1: No future dates
            if (order.tradeDate.isAfter(today)) {
                errors.add(
                    CsvRowError(
                        rowNumber = rowNumber,
                        field = "trade_date",
                        message = "Trade date ${order.tradeDate} is in the future (today is $today)",
                    ),
                )
            }

            // Rule 2: No duplicate vs. existing database records
            if (order.zerodhaOrderId in existingOrderIds) {
                errors.add(
                    CsvRowError(
                        rowNumber = rowNumber,
                        field = "zerodha_order_id",
                        message = "Order ID '${order.zerodhaOrderId}' already exists in the database",
                    ),
                )
            }

            // Rule 3: No intra-file duplicates
            if (!seenInBatch.add(order.zerodhaOrderId)) {
                errors.add(
                    CsvRowError(
                        rowNumber = rowNumber,
                        field = "zerodha_order_id",
                        message = "Order ID '${order.zerodhaOrderId}' appears more than once in this file",
                    ),
                )
            }
        }

        return errors
    }
}
