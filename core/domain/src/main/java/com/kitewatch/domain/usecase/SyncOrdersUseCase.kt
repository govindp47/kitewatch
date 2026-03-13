package com.kitewatch.domain.usecase

import com.kitewatch.domain.engine.ChargeCalculator
import com.kitewatch.domain.engine.ComputedHolding
import com.kitewatch.domain.engine.GttAction
import com.kitewatch.domain.engine.GttAutomationEngine
import com.kitewatch.domain.engine.HoldingsComputationEngine
import com.kitewatch.domain.engine.TargetPriceCalculator
import com.kitewatch.domain.error.AppError
import com.kitewatch.domain.model.ChargeBreakdown
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.model.SyncResult
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionSource
import com.kitewatch.domain.model.TransactionType
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.AlertSeverity
import com.kitewatch.domain.repository.AlertType
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.repository.PersistentAlert
import com.kitewatch.domain.repository.RemoteHolding
import com.kitewatch.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Wraps an [AppError] as a [Throwable] so it can be carried by [kotlin.Result].
 */
class AppException(
    val error: AppError,
) : Exception(error.javaClass.simpleName)

/**
 * Orchestrates a full order sync cycle (§5.1 of 04_DOMAIN_ENGINE_DESIGN.md).
 *
 * All I/O is via injected repository interfaces; zero Android dependencies.
 * The [mutex] is always released — including on exception — via [Mutex.withLock].
 *
 * @param clock     Injected for testable weekday-guard evaluation
 * @param mutex     Injected so tests can verify concurrency behaviour
 */
@Suppress("LongParameterList") // All params are required domain dependencies; no natural grouping.
class SyncOrdersUseCase(
    private val kiteConnectRepo: KiteConnectRepository,
    private val orderRepo: OrderRepository,
    private val holdingRepo: HoldingRepository,
    private val transactionRepo: TransactionRepository,
    private val chargeRateRepo: ChargeRateRepository,
    private val alertRepo: AlertRepository,
    private val gttRepo: GttRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val mutex: Mutex = Mutex(),
) {
    /** BR-09: skip sync on weekends; acquire mutex and delegate to [executeSync]. */
    suspend fun execute(): Result<SyncResult> {
        if (isWeekend(LocalDate.now(clock))) {
            return Result.success(SyncResult.Skipped("Weekend — no trading day"))
        }
        // Mutex.withLock guarantees release even if executeSync throws.
        return mutex.withLock { executeSync() }
    }

    @Suppress("ReturnCount") // Orchestration function — each step has an independent failure path.
    private suspend fun executeSync(): Result<SyncResult> {
        // ── Step 1: Fetch remote orders ───────────────────────────────────────────
        val remoteOrdersResult = kiteConnectRepo.fetchTodaysOrders()
        if (remoteOrdersResult.isFailure) {
            return Result.failure(
                AppException(AppError.NetworkError.Unexpected(remoteOrdersResult.exceptionOrNull())),
            )
        }
        val remoteOrders = remoteOrdersResult.getOrThrow()

        // ── Step 2: Deduplicate ───────────────────────────────────────────────────
        val newOrders = remoteOrders.filter { !orderRepo.existsByZerodhaId(it.zerodhaOrderId) }
        if (newOrders.isEmpty()) {
            return Result.success(SyncResult.NoNewOrders)
        }

        // ── Step 3: Fetch remote holdings for BR-07 verification ─────────────────
        val remoteHoldingsResult = kiteConnectRepo.fetchHoldings()
        if (remoteHoldingsResult.isFailure) {
            return Result.failure(AppException(AppError.DomainError.HoldingsFetchFailed))
        }
        val remoteHoldings = remoteHoldingsResult.getOrThrow()

        // ── Step 4: BR-07 — verify holdings match before persistence ─────────────
        val localHoldings = holdingRepo.getAll()
        val diffs = verifyHoldings(remoteHoldings, localHoldings)
        if (diffs.isNotEmpty()) {
            alertRepo.insert(
                PersistentAlert(
                    alertType = AlertType.HOLDINGS_MISMATCH,
                    severity = AlertSeverity.CRITICAL,
                    payload = diffs.joinToString("; "),
                    createdAt = Instant.now(clock),
                ),
            )
            return Result.failure(AppException(AppError.DomainError.HoldingsMismatch(diffs)))
        }

        // ── Step 5: Fetch charge rates (proceed without if absent) ────────────────
        val chargeRates = chargeRateRepo.getCurrentRates()

        // ── Step 6: Insert orders + charge/equity transactions ────────────────────
        val chargesByOrderId = mutableMapOf<Long, ChargeBreakdown>()
        for (order in newOrders) {
            val insertedId = orderRepo.insert(order)
            transactionRepo.insert(equityTransaction(order))
            if (chargeRates != null) {
                val charges =
                    ChargeCalculator.calculate(
                        tradeValue = order.totalValue,
                        orderType = order.orderType,
                        exchange = order.exchange,
                        rates = chargeRates,
                    )
                chargesByOrderId[insertedId] = charges
                transactionRepo.insertAll(chargeTransactions(order, charges))
            }
        }

        // ── Step 7: Recompute holdings from full order history ────────────────────
        val allOrders = orderRepo.getAll()
        val computedHoldings = HoldingsComputationEngine.compute(allOrders, chargesByOrderId)

        // ── Step 8: Upsert computed holdings ──────────────────────────────────────
        val now = Instant.now(clock)
        val updatedHoldings =
            computedHoldings.map { computed ->
                upsertComputedHolding(computed, localHoldings, chargeRates, now)
            }

        // ── Step 9: Evaluate GTT actions (requires charge rates for price calc) ───
        val gttActionCount = evaluateGttActions(computedHoldings, updatedHoldings, chargeRates)

        return Result.success(SyncResult.Success(newOrderCount = newOrders.size, updatedGttCount = gttActionCount))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * Compares CNC remote holdings against local active holdings (BR-07).
     * Returns diff descriptions for any quantity mismatch; empty list on match.
     */
    private fun verifyHoldings(
        remoteHoldings: List<RemoteHolding>,
        localHoldings: List<Holding>,
    ): List<String> {
        val remoteMap =
            remoteHoldings
                .filter { it.product == CNC_PRODUCT }
                .associate { it.tradingSymbol to it.quantity }
        val localMap =
            localHoldings
                .filter { it.quantity > 0 }
                .associate { it.stockCode to it.quantity }
        return (remoteMap.keys + localMap.keys).toSet().mapNotNull { stockCode ->
            val remoteQty = remoteMap[stockCode] ?: 0
            val localQty = localMap[stockCode] ?: 0
            if (remoteQty != localQty) "$stockCode: remote=$remoteQty local=$localQty" else null
        }
    }

    private fun equityTransaction(order: Order): Transaction =
        Transaction(
            transactionId = 0L,
            type = if (order.orderType == OrderType.BUY) TransactionType.EQUITY_BUY else TransactionType.EQUITY_SELL,
            referenceId = order.zerodhaOrderId,
            stockCode = order.stockCode,
            amount = if (order.orderType == OrderType.BUY) -order.totalValue else order.totalValue,
            transactionDate = order.tradeDate,
            description = "${order.orderType} ${order.quantity}×${order.stockCode}",
            source = TransactionSource.SYNC,
        )

    private fun chargeTransactions(
        order: Order,
        charges: ChargeBreakdown,
    ): List<Transaction> =
        buildList {
            val ref = order.zerodhaOrderId
            val stock = order.stockCode
            val date = order.tradeDate
            if (charges.stt.value > 0) {
                add(chargeTx(TransactionType.STT_CHARGE, ref, stock, -charges.stt, date, "STT"))
            }
            if (charges.exchangeTxn.value > 0) {
                add(chargeTx(TransactionType.EXCHANGE_CHARGE, ref, stock, -charges.exchangeTxn, date, "Exchange txn"))
            }
            if (charges.sebiCharges.value > 0) {
                add(chargeTx(TransactionType.SEBI_CHARGE, ref, stock, -charges.sebiCharges, date, "SEBI"))
            }
            if (charges.stampDuty.value > 0) {
                add(chargeTx(TransactionType.STAMP_DUTY_CHARGE, ref, stock, -charges.stampDuty, date, "Stamp duty"))
            }
            if (charges.gst.value > 0) {
                add(chargeTx(TransactionType.GST_CHARGE, ref, stock, -charges.gst, date, "GST"))
            }
        }

    private suspend fun upsertComputedHolding(
        computed: ComputedHolding,
        localHoldings: List<Holding>,
        chargeRates: ChargeRateSnapshot?,
        now: Instant,
    ): Holding {
        val existing = localHoldings.firstOrNull { it.stockCode == computed.stockCode }
        val profitTarget = existing?.profitTarget ?: ProfitTarget.Percentage(DEFAULT_PROFIT_TARGET_BPS)
        val targetSellPrice = computeTargetSellPrice(computed, profitTarget, chargeRates, existing)
        val holding =
            Holding(
                holdingId = existing?.holdingId ?: 0L,
                stockCode = computed.stockCode,
                stockName = existing?.stockName ?: computed.stockCode,
                quantity = computed.quantity,
                avgBuyPrice = computed.avgBuyPrice,
                investedAmount = computed.investedAmount,
                totalBuyCharges = computed.totalBuyCharges,
                profitTarget = profitTarget,
                targetSellPrice = targetSellPrice,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        holdingRepo.upsert(holding)
        return holding
    }

    private fun computeTargetSellPrice(
        computed: ComputedHolding,
        profitTarget: ProfitTarget,
        chargeRates: ChargeRateSnapshot?,
        existing: Holding?,
    ): Paisa =
        if (chargeRates != null && computed.quantity > 0) {
            TargetPriceCalculator.compute(
                avgBuyPrice = computed.avgBuyPrice,
                quantity = computed.quantity,
                profitTarget = profitTarget,
                investedAmount = computed.investedAmount,
                buyCharges = computed.totalBuyCharges,
                chargeRates = chargeRates,
            )
        } else {
            existing?.targetSellPrice ?: Paisa.ZERO
        }

    private suspend fun evaluateGttActions(
        computedHoldings: List<ComputedHolding>,
        updatedHoldings: List<Holding>,
        chargeRates: ChargeRateSnapshot?,
    ): Int {
        if (chargeRates == null) return 0
        val existingGtts = gttRepo.observeActive().first().associateBy { it.stockCode }
        val profitTargets = updatedHoldings.associate { it.stockCode to it.profitTarget }
        val actions =
            GttAutomationEngine.evaluate(
                holdings = computedHoldings,
                existingGtts = existingGtts,
                chargeRates = chargeRates,
                profitTargets = profitTargets,
            )
        return actions.count { it !is GttAction.NoAction }
    }

    private fun chargeTx(
        type: TransactionType,
        ref: String,
        stock: String,
        amount: Paisa,
        date: java.time.LocalDate,
        description: String,
    ) = Transaction(0L, type, ref, stock, amount, date, description, TransactionSource.SYNC)

    private companion object {
        const val CNC_PRODUCT = "CNC"
        const val DEFAULT_PROFIT_TARGET_BPS = 500 // 5%
    }
}
