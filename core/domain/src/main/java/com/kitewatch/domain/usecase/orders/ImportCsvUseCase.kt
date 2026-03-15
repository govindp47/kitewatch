package com.kitewatch.domain.usecase.orders

import com.kitewatch.domain.engine.ChargeCalculator
import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.OrderRepository
import java.io.InputStream
import java.time.Instant

// ── Result and error types ────────────────────────────────────────────────────

/**
 * Outcome of a successful [ImportCsvUseCase.execute] call.
 *
 * @param newOrderCount        Orders written to the database.
 * @param skippedDuplicateCount Orders that were already present (silently skipped — BR-12).
 * @param estimatedChargesCount Orders whose charges were computed using fallback rates because
 *                              no live [ChargeRateSnapshot] was available. These are imported
 *                              successfully; the count is a transparency signal for the UI.
 */
data class ImportResult(
    val newOrderCount: Int,
    val skippedDuplicateCount: Int,
    val estimatedChargesCount: Int,
)

/** A single row-level CSV validation error surfaced to the caller. */
data class ImportRowError(
    val rowNumber: Int,
    val field: String,
    val message: String,
)

/**
 * Thrown when [ImportCsvUseCase.execute] returns [Result.failure] because the CSV file
 * contains format or field errors. No data is written when this exception is produced.
 *
 * Implements BR-11: all-or-nothing import semantics.
 */
class ImportValidationException(
    val errors: List<ImportRowError>,
) : Exception("CSV import rejected: ${errors.size} validation error(s)")

// ── Port interfaces ───────────────────────────────────────────────────────────

/**
 * Parses a CSV [InputStream] into domain [Order]s, performing format-detection and
 * field-level validation.
 *
 * Implemented by the `:infra-csv` adapter that delegates to
 * [com.kitewatch.infra.csv.CsvParser].
 */
interface CsvParsePort {
    suspend fun parse(inputStream: InputStream): CsvParsePortResult
}

sealed class CsvParsePortResult {
    /** All rows valid; [orders] are ready for duplicate-checking and persistence. */
    data class Success(
        val orders: List<Order>,
    ) : CsvParsePortResult()

    /** One or more rows failed format or field validation; import must be rejected. */
    data class Failure(
        val errors: List<ImportRowError>,
    ) : CsvParsePortResult()
}

/**
 * Creates a local safety backup immediately before a destructive import operation.
 *
 * Implemented by the `:infra-backup` adapter that delegates to `CreateBackupUseCase`.
 */
fun interface PreImportBackupPort {
    suspend fun createLocalBackup(accountId: String): Result<Unit>
}

/**
 * Executes the full multi-table import within a single atomic database transaction.
 *
 * The implementation (in `:core-data`) is responsible for:
 * 1. Inserting [orders] — using `ON CONFLICT IGNORE` so any race-condition duplicates
 *    are silently dropped at the DB level as a final safety net.
 * 2. Upserting derived holdings via [HoldingsComputationEngine].
 * 3. Inserting charge ledger entries for each entry in [chargesByZerodhaId].
 * 4. Invalidating / updating the P&L monthly cache for all affected months.
 *
 * If any step throws, the entire transaction must roll back (BR-11).
 */
interface ImportTransactionPort {
    suspend fun runImport(
        orders: List<Order>,
        chargesByZerodhaId: Map<String, ChargeBreakdown>,
    )
}

// ── Use case ──────────────────────────────────────────────────────────────────

class ImportCsvUseCase(
    private val csvParsePort: CsvParsePort,
    private val preImportBackupPort: PreImportBackupPort,
    private val importTransactionPort: ImportTransactionPort,
    private val orderRepository: OrderRepository,
    private val chargeRateRepository: ChargeRateRepository,
) {
    /**
     * Orchestrates the complete CSV import flow.
     *
     * Steps:
     * 1. Pre-import safety backup — best-effort; failure is logged and does **not** block import.
     * 2. Parse and validate the CSV stream — any row error rejects the entire file (BR-11).
     * 3. Deduplicate against existing DB records — duplicates silently skipped (BR-12).
     * 4. Calculate charges — uses live rates when available; falls back to [FALLBACK_CHARGE_RATES].
     * 5. Atomic all-or-nothing write — [ImportTransactionPort.runImport] handles the transaction.
     *
     * @param inputStream  Open stream for the CSV file; the caller is responsible for closing it.
     * @param accountId    Bound Zerodha account ID — required for the pre-import backup call.
     * @return [Result.success] with [ImportResult] on success, or
     *         [Result.failure] wrapping [ImportValidationException] when the CSV is invalid.
     */
    suspend fun execute(
        inputStream: InputStream,
        accountId: String,
    ): Result<ImportResult> {
        // Step 1 — Pre-import safety backup (best-effort; per 08_BACKUP_AND_RECOVERY.md §5.3)
        val backupResult = preImportBackupPort.createLocalBackup(accountId)
        if (backupResult.isFailure) {
            // :core-domain has no Android/Timber dependency; use stderr for the warning.
            System.err.println(
                "[ImportCsvUseCase] WARNING: pre-import backup failed " +
                    "(${backupResult.exceptionOrNull()?.message}). Proceeding with import.",
            )
        }

        // Step 2 — Parse CSV (format + field validation)
        val parseResult = csvParsePort.parse(inputStream)
        if (parseResult is CsvParsePortResult.Failure) {
            // BR-11: invalid file → reject entirely, write nothing
            return Result.failure(ImportValidationException(parseResult.errors))
        }
        val parsedOrders = (parseResult as CsvParsePortResult.Success).orders

        // Step 3 — Duplicate detection (BR-12: silently skip, not an error)
        val existingIds: Set<String> =
            orderRepository.getAll().mapTo(mutableSetOf()) { it.zerodhaOrderId }

        val (newOrders, duplicates) =
            parsedOrders.partition { it.zerodhaOrderId !in existingIds }

        // Steps 4 & 5 — only when there are new orders to write
        var estimatedChargesCount = 0
        if (newOrders.isNotEmpty()) {
            val liveRates: ChargeRateSnapshot? = chargeRateRepository.getCurrentRates()
            val effectiveRates = liveRates ?: FALLBACK_CHARGE_RATES
            estimatedChargesCount = if (liveRates == null) newOrders.size else 0

            val chargesByZerodhaId: Map<String, ChargeBreakdown> =
                newOrders.associate { order ->
                    order.zerodhaOrderId to
                        ChargeCalculator.calculate(
                            tradeValue = order.totalValue,
                            orderType = order.orderType,
                            exchange = order.exchange,
                            rates = effectiveRates,
                        )
                }

            // Step 5 — Atomic all-or-nothing write (BR-11)
            importTransactionPort.runImport(
                orders = newOrders,
                chargesByZerodhaId = chargesByZerodhaId,
            )
        }

        return Result.success(
            ImportResult(
                newOrderCount = newOrders.size,
                skippedDuplicateCount = duplicates.size,
                estimatedChargesCount = estimatedChargesCount,
            ),
        )
    }

    companion object {
        /**
         * Hardcoded fallback [ChargeRateSnapshot] used when no live rates are stored.
         *
         * Values reflect Zerodha equity delivery charges as of 2024.  Orders calculated
         * using these rates are included in [ImportResult.estimatedChargesCount] so the
         * UI can surface a "charges are estimated" banner.
         */
        val FALLBACK_CHARGE_RATES =
            ChargeRateSnapshot(
                brokerageDeliveryMilliBps = 0, // ₹0 brokerage — Zerodha equity delivery
                sttBuyMilliBps = 10_000, // 0.1%
                sttSellMilliBps = 10_000, // 0.1%
                exchangeNseMilliBps = 297, // 0.00297%
                exchangeBseMilliBps = 375, // 0.00375%
                gstMilliBps = 1_800_000, // 18%
                sebiChargePerCrorePaisa = Paisa(1_000L), // ₹10 / crore
                stampDutyBuyMilliBps = 1_500, // 0.015%
                dpChargesPerScriptPaisa = Paisa(1_580L), // ₹15.80 / script / sell day
                fetchedAt = Instant.EPOCH,
            )
    }
}
