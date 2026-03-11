# 04 — Domain Engine Design

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## 1. Domain Model Philosophy

### 1.1 Core Principles

1. **Domain models are pure Kotlin.** No Android framework references, no Room annotations, no serialization annotations. Domain models live in `:core-domain` and have zero external dependencies.
2. **Value objects for monetary amounts.** All money passes through a `Paisa` value class that wraps `Long` and provides type-safe arithmetic, preventing accidental mixing of raw integers with monetary values.
3. **Entities are identity-based.** `Order`, `Holding`, `Transaction`, `GttRecord` are entities with identity (by ID or business key). Equality is by identity, not value.
4. **Immutable by default.** Domain models are `data class` with `val` properties. State transitions produce new instances.
5. **Business rules live in the Domain layer.** No business logic in ViewModels, DAOs, or Workers. These layers delegate to UseCases and domain services.
6. **Fail loudly.** Domain operations return `Result<T>` (sealed success/failure). No exception swallowing. Every failure is typed and surfaced.

### 1.2 Domain Model Hierarchy

```
core-domain/
├── model/
│   ├── Paisa.kt                    -- Value class for monetary amounts
│   ├── Order.kt                    -- Domain entity
│   ├── Holding.kt                  -- Domain entity with computed properties
│   ├── Transaction.kt              -- Immutable ledger entry
│   ├── FundEntry.kt                -- User fund addition/withdrawal
│   ├── GttRecord.kt                -- GTT state
│   ├── ChargeRateSnapshot.kt       -- Current charge rates
│   ├── ChargeBreakdown.kt          -- Computed charges per order
│   ├── PnlSummary.kt              -- Realized P&L for a date range
│   ├── ProfitTarget.kt            -- Sealed: Percentage | Absolute
│   ├── AccountBinding.kt          -- Bound account info
│   └── SyncResult.kt              -- Result of a sync operation
├── engine/
│   ├── ChargeCalculator.kt         -- Stateless charge computation
│   ├── PnlCalculator.kt           -- P&L aggregation logic
│   ├── HoldingsVerifier.kt        -- Holdings mismatch detection
│   ├── TargetPriceCalculator.kt   -- Target sell price computation
│   ├── DuplicateDetector.kt       -- CSV/fund entry duplicate detection
│   └── FundBalanceCalculator.kt   -- Running fund balance computation
├── usecase/
│   ├── SyncOrdersUseCase.kt
│   ├── ReconcileFundUseCase.kt
│   ├── PlaceGttUseCase.kt
│   ├── UpdateProfitTargetUseCase.kt
│   ├── ImportCsvUseCase.kt
│   ├── CalculatePnlUseCase.kt
│   ├── AddFundEntryUseCase.kt
│   ├── CreateBackupUseCase.kt
│   ├── RestoreBackupUseCase.kt
│   └── RefreshChargeRatesUseCase.kt
├── repository/                      -- Interfaces only (implemented in :core-data)
│   ├── OrderRepository.kt
│   ├── HoldingRepository.kt
│   ├── TransactionRepository.kt
│   ├── FundRepository.kt
│   ├── GttRepository.kt
│   ├── ChargeRateRepository.kt
│   ├── KiteConnectRepository.kt
│   └── AlertRepository.kt
└── error/
    └── AppError.kt                  -- Sealed error hierarchy
```

---

## 2. Core Value Objects

### 2.1 Paisa — Monetary Value Class

```kotlin
@JvmInline
value class Paisa(val value: Long) : Comparable<Paisa> {
    operator fun plus(other: Paisa) = Paisa(value + other.value)
    operator fun minus(other: Paisa) = Paisa(value - other.value)
    operator fun times(quantity: Int) = Paisa(value * quantity)
    operator fun times(quantity: Long) = Paisa(value * quantity)
    operator fun div(divisor: Int): Paisa {
        require(divisor != 0) { "Division by zero" }
        return Paisa(value / divisor)
    }
    operator fun unaryMinus() = Paisa(-value)

    fun abs() = Paisa(kotlin.math.abs(value))
    fun isPositive() = value > 0
    fun isNegative() = value < 0
    fun isZero() = value == 0L

    /**
     * Multiply by a rate in basis points (1 basis point = 0.01%).
     * Example: Paisa(1000000) * 25 basis points = Paisa(250)
     * Formula: (value * basisPoints) / 10_000
     * Uses Long arithmetic with rounding.
     */
    fun applyBasisPoints(basisPoints: Int): Paisa {
        // Round half-up: (value * bps + 5000) / 10000
        val result = (value * basisPoints + 5000) / 10_000
        return Paisa(result)
    }

    /**
     * Convert to BigDecimal in rupees (divides by 100).
     * Used ONLY at display and reporting boundaries.
     */
    fun toRupees(): BigDecimal = BigDecimal(value).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

    override fun compareTo(other: Paisa): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Paisa(0)
        fun fromRupees(rupees: BigDecimal): Paisa = Paisa(rupees.multiply(BigDecimal(100)).toLong())
        fun fromRupees(rupees: Double): Paisa = Paisa((rupees * 100).roundToLong())
    }
}
```

### 2.2 ProfitTarget — Sealed Type

```kotlin
sealed interface ProfitTarget {
    val displayValue: String

    /**
     * @param value Basis points: 500 = 5.00%
     */
    data class Percentage(val basisPoints: Int) : ProfitTarget {
        init { require(basisPoints >= 0) { "Profit target percentage cannot be negative" } }
        override val displayValue: String get() = "${basisPoints / 100}.${basisPoints % 100}%"
    }

    /**
     * @param amount Absolute profit target in Paisa
     */
    data class Absolute(val amount: Paisa) : ProfitTarget {
        init { require(amount.value >= 0) { "Profit target amount cannot be negative" } }
        override val displayValue: String get() = "₹${amount.toRupees()}"
    }

    fun computeTargetProfit(investedAmount: Paisa): Paisa = when (this) {
        is Percentage -> investedAmount.applyBasisPoints(basisPoints)
        is Absolute -> amount
    }
}
```

---

## 3. Business Rule Enforcement

### 3.1 Rule Catalog

| ID | Rule | Enforcement Point | Failure Behavior |
|---|---|---|---|
| BR-01 | One Zerodha account per installation | `AccountBindingRepository.bind()` — ignores if already bound | Returns `AlreadyBound` error |
| BR-02 | Backup/import account ID must match bound account | `AccountValidator.validate()` | Rejects import with `AccountMismatch` error |
| BR-03 | Holdings quantity ≥ 0 | `Holding` constructor | `IllegalArgumentException` (programming error) |
| BR-04 | Order price and quantity > 0 | `Order` constructor | `IllegalArgumentException` |
| BR-05 | Fund entry amount > 0 | `FundEntry` constructor | `IllegalArgumentException` |
| BR-06 | Charge rates must exist before charge calculation | `ChargeCalculator.calculate()` | Returns `ChargeRatesMissing` error |
| BR-07 | Holdings verification must pass before order persistence | `SyncOrdersUseCase` | Aborts sync, emits `HoldingsMismatch` |
| BR-08 | GTT verification must pass before local GTT record update | `PlaceGttUseCase` | Emits `GttVerificationFailed` alert |
| BR-09 | No weekend execution for scheduled tasks | `WeekdayGuard.isTradingDay()` | Worker returns SUCCESS immediately (no-op) |
| BR-10 | Transactions are append-only | `TransactionRepository` interface — no update/delete methods | Compile-time: methods don't exist |
| BR-11 | CSV import is all-or-nothing (no partial import) | `ImportCsvUseCase` | Validation failures reject entire file |
| BR-12 | Duplicate orders rejected by business key | `zerodha_order_id UNIQUE` + app-level check | Duplicate silently skipped (INSERT IGNORE) |
| BR-13 | Fund reconciliation auto-adjustment only within tolerance | `ReconcileFundUseCase` | Outside tolerance → alert; within → auto-adjust + log |
| BR-14 | Gmail-detected fund entries require user confirmation | `GmailScanUseCase` returns pending entries | UI presents confirmation before persistence |
| BR-15 | GTT only for single-trigger sell orders on equity delivery | `GttEngine` | Ignores non-equity, non-sell GTT types |

---

## 4. State Transition Logic

### 4.1 Holding Lifecycle

```
                 ┌──────────────┐
                 │ NON_EXISTENT │
                 └──────┬───────┘
                        │ Buy order synced (new stock)
                        ▼
                 ┌──────────────┐
          ┌─────│    ACTIVE     │◀────────┐
          │     │ (quantity > 0)│          │
          │     └──────┬───────┘          │
          │            │                   │
          │  Partial   │ Additional        │
          │  sell      │ buy order         │
          │  synced    │ synced            │
          │            │                   │
          │     ┌──────▼───────┐          │
          │     │    ACTIVE     │──────────┘
          │     │ (qty updated, │  (avg price recalculated)
          │     │  avg price    │
          │     │  recalculated)│
          │     └──────┬───────┘
          │            │
          │            │ Full sell (all qty sold)
          ▼            ▼
   ┌──────────────────────────┐
   │       ZERO_QUANTITY       │
   │  (retained in DB, hidden  │
   │   from Holdings screen)   │
   └───────────────────────────┘
```

**Average Buy Price Recalculation on Additional Buy:**

```
Pseudocode: recalculate_avg_price(holding, newBuyOrder)

  new_invested = holding.invested_amount + newBuyOrder.total_value
  new_quantity  = holding.quantity + newBuyOrder.quantity
  new_avg_price = new_invested / new_quantity   // Integer division (paisa)

  return holding.copy(
    quantity = new_quantity,
    invested_amount = new_invested,
    avg_buy_price = new_avg_price,
    total_buy_charges = holding.total_buy_charges + newBuyOrder.charges.total(),
    // Recalculate target sell price with new averages
    target_sell_price = calculateTargetSellPrice(new_invested, new_quantity, holding.profitTarget, chargeRates)
  )
```

### 4.2 GTT Record Lifecycle

```
                 ┌───────────────────┐
                 │ PENDING_CREATION   │
                 │ (buy order synced, │
                 │  GTT not yet placed│
                 │  with Zerodha)     │
                 └────────┬──────────┘
                          │ GTT API call succeeds
                          │ + Verification passes
                          ▼
                 ┌───────────────────┐
          ┌──────│      ACTIVE       │◀──────────────────┐
          │      │ (zerodha_gtt_id   │                    │
          │      │  assigned)        │   GTT updated      │
          │      └────────┬──────────┘   (qty/price       │
          │               │              change)          │
          │               │                               │
          │  ┌────────────┼─────────────┐                │
          │  │            │             │                 │
          │  │  Triggered │  User       │  Holding qty   │
          │  │  by market │  manually   │  changes       │
          │  │            │  modifies   │  (partial sell) │
          │  │            │  in Zerodha │                 │
          │  ▼            ▼             │                 │
          │  ┌──────┐  ┌──────────────┐│                │
          │  │TRIGRD │  │MANUAL_OVRD  ││  ┌────────────┐│
          │  │      │  │ DETECTED     ││  │PENDING_UPD ││
          │  └──┬───┘  └──────┬───────┘│  └─────┬──────┘│
          │     │             │        │        │       │
          │     │    User     │        │  API   │       │
          │     │   decides:  │        │  call  │       │
          │     │  keep/revert│        │ succeeds       │
          │     │             │        │        │       │
          │     │   ┌─────────▼──┐     │        └───────┘
          │     │   │  ACTIVE or │     │
          │     │   │  (user's   │     │
          │     │   │   choice)  │     │
          │     │   └────────────┘     │
          │     │                      │
          │     │  Holding sold to 0   │
          │     ▼                      ▼
          │  ┌─────────────────────────────┐
          └─▶│        ARCHIVED             │
             │  (is_archived = 1)          │
             │  Retained in DB for history │
             └─────────────────────────────┘
```

### 4.3 Sync Event Lifecycle

```
RUNNING ──┬──▶ SUCCESS     (normal completion)
          ├──▶ FAILED      (error; no data modified)
          ├──▶ PARTIAL     (some sub-tasks succeeded, some failed — e.g., orders synced but GTT failed)
          └──▶ SKIPPED     (weekend guard triggered; no work done)
```

---

## 5. Transaction Processing Logic

### 5.1 Order Sync Transaction (Atomic)

```
Pseudocode: SyncOrdersUseCase.execute()

  INPUT:  None (triggered by worker or user action)
  OUTPUT: Result<SyncResult>

  1. CHECK weekday guard
     IF NOT trading_day:
       RETURN Success(SyncResult.Skipped)

  2. ACQUIRE mutex("order_sync")

  3. LOG sync_event(ORDER_SYNC, RUNNING)

  4. FETCH remote_orders = kiteConnectRepo.fetchTodaysOrders()
     ON FAILURE:
       LOG sync_event(FAILED, error)
       EMIT AppEvent.OrderSyncFailed(TransientError)
       RELEASE mutex
       RETURN Failure(NetworkError)

  5. FILTER new_orders = remote_orders.filter { !orderRepo.exists(it.zerodhaOrderId) }
     IF new_orders.isEmpty():
       LOG sync_event(SUCCESS, "No new orders")
       RELEASE mutex
       RETURN Success(SyncResult.NoNewOrders)

  6. FILTER equity_delivery_orders = new_orders.filter { it.isEquityDelivery() }
     IF equity_delivery_orders.isEmpty():
       LOG sync_event(SUCCESS, "No equity delivery orders")
       RELEASE mutex
       RETURN Success(SyncResult.NoNewOrders)

  7. FETCH remote_holdings = kiteConnectRepo.fetchHoldings()
     ON FAILURE:
       LOG sync_event(FAILED, "Holdings fetch failed after orders received")
       EMIT AppEvent.OrderSyncFailed(HoldingsFetchFailed)
       RELEASE mutex
       RETURN Failure(NetworkError)

  8. VERIFY verification_result = holdingsVerifier.verify(remote_holdings, localHoldings)
     IF verification_result is MISMATCH:
       LOG sync_event(FAILED, "Holdings mismatch: ${verification_result.diffs}")
       alertRepo.insert(PersistentAlert(HOLDINGS_MISMATCH, CRITICAL, diffs))
       EMIT AppEvent.OrderSyncFailed(HoldingsMismatch(diffs))
       RELEASE mutex
       RETURN Failure(HoldingsMismatch(diffs))

  9. ACQUIRE charge_rates = chargeRateRepo.getCurrentRates()
     IF charge_rates is NULL:
       // Proceed without charges — log warning
       EMIT AppEvent.ChargeRatesMissing

  10. BEGIN DATABASE TRANSACTION:

      a. FOR each order IN equity_delivery_orders:
           orderRepo.insert(order)

           // Calculate charges
           IF charge_rates != NULL:
             charges = chargeCalculator.calculate(order, charge_rates)
             transactionRepo.insertChargeTransactions(order, charges)

           // Insert equity value transaction
           transactionRepo.insertEquityTransaction(order)

      b. FOR each affected_stock IN equity_delivery_orders.groupByStock():
           IF order_type == BUY:
             holdingRepo.addToHolding(stock, orderQty, orderValue, charges)
             orderHoldingsLinkRepo.insert(orderId, holdingId, qty)
           ELSE IF order_type == SELL:
             holdingRepo.reduceHolding(stock, orderQty, soldCostBasis)
             orderHoldingsLinkRepo.insert(orderId, holdingId, qty)

      c. Recalculate target_sell_price for each modified holding.

      d. Update pnl_monthly_cache for affected months.

      COMMIT TRANSACTION.

  11. LOG sync_event(SUCCESS, details)

  12. RELEASE mutex

  13. // Trigger GTT update (non-blocking, separate worker)
      buy_orders = equity_delivery_orders.filter { it.type == BUY }
      IF buy_orders.isNotEmpty():
        workerHandoff.insert("gtt_update", affected_stock_codes)
        enqueue GttUpdateWorker

  14. // Trigger fund reconciliation (non-blocking)
      enqueue ReconciliationWorker

  15. EMIT AppEvent.OrderSyncCompleted(new_orders.size)

  16. RETURN Success(SyncResult.Synced(orderCount, stocksAffected))
```

### 5.2 Fund Reconciliation Transaction

```
Pseudocode: ReconcileFundUseCase.execute()

  INPUT:  tolerance: Paisa (from user settings)
  OUTPUT: Result<ReconciliationResult>

  1. FETCH remote_balance = kiteConnectRepo.fetchFundBalance()
     ON FAILURE:
       LOG sync_event(FUND_RECONCILIATION, FAILED)
       RETURN Failure(NetworkError)

  2. COMPUTE local_balance = fundBalanceCalculator.computeCurrentBalance()
     // Queries transactions table for running total

  3. COMPUTE difference = remote_balance - local_balance

  4. IF difference.abs() <= tolerance:
       // Auto-adjust
       IF difference != ZERO:
         fund_entry = FundEntry(MISC_ADJUSTMENT, difference, today, "Auto-reconciliation adjustment")
         fundRepo.insert(fund_entry)
         transactionRepo.insert(Transaction(MISC_ADJUSTMENT, difference, "Auto-reconciliation"))

       LOG sync_event(SUCCESS, "Within tolerance. Adjustment: ${difference}")
       EMIT AppEvent.FundReconciliationCompleted(adjustment = difference)
       RETURN Success(WithinTolerance(difference))

  5. ELSE:
       // Exceeds tolerance — alert user
       alertRepo.insert(PersistentAlert(
         FUND_MISMATCH, CRITICAL,
         payload = { local_balance, remote_balance, difference, tolerance }
       ))
       LOG sync_event(SUCCESS, "Mismatch exceeds tolerance: ${difference}")
       EMIT AppEvent.FundMismatchDetected(local_balance, remote_balance)
       RETURN Success(ExceedsTolerance(local_balance, remote_balance, difference))
```

---

## 6. Charge Calculator Engine

### 6.1 Algorithm

```kotlin
object ChargeCalculator {

    /**
     * Calculate all charges for a single equity delivery order.
     * All arithmetic is in Paisa (Long) to avoid floating-point errors.
     *
     * @param order       The executed order (BUY or SELL)
     * @param rates       Current charge rate snapshot
     * @return            ChargeBreakdown with individual charge amounts
     */
    fun calculate(order: Order, rates: ChargeRateSnapshot): ChargeBreakdown {
        val turnover = order.totalValue  // Paisa

        // 1. Brokerage: Zerodha charges ₹0 for equity delivery
        val brokerage = turnover.applyBasisPoints(rates.brokerageDeliveryBps)
        // Expected: Paisa(0) for Zerodha delivery

        // 2. STT (Securities Transaction Tax)
        val stt = when (order.type) {
            OrderType.BUY  -> turnover.applyBasisPoints(rates.sttBuyBps)   // 0.1%
            OrderType.SELL -> turnover.applyBasisPoints(rates.sttSellBps)  // 0.025%
        }

        // 3. Exchange Transaction Charges
        val exchangeRate = when (order.exchange) {
            Exchange.NSE -> rates.exchangeNseBps
            Exchange.BSE -> rates.exchangeBseBps
        }
        val exchangeCharges = turnover.applyBasisPoints(exchangeRate)

        // 4. GST: 18% on (brokerage + exchange charges)
        val gstBase = brokerage + exchangeCharges
        val gst = gstBase.applyBasisPoints(rates.gstBps)  // 1800 bps = 18%

        // 5. SEBI Turnover Fees: ₹10 per crore
        // Formula: turnover_in_paisa * 10 / 10_000_000_00 (1 crore in paisa)
        val sebi = Paisa((turnover.value * rates.sebiFeePerCrorePaisa) / 1_000_000_00L)

        // 6. Stamp Duty: Only on BUY side
        val stampDuty = when (order.type) {
            OrderType.BUY  -> turnover.applyBasisPoints(rates.stampDutyBps)  // 0.015%
            OrderType.SELL -> Paisa.ZERO
        }

        // 7. DP Charges: Only on SELL side, flat fee per script per day
        val dpCharges = when (order.type) {
            OrderType.BUY  -> Paisa.ZERO
            OrderType.SELL -> rates.dpChargesPerScriptPaisa  // e.g., ₹15.80
        }

        val total = brokerage + stt + exchangeCharges + gst + sebi + stampDuty + dpCharges

        return ChargeBreakdown(
            brokerage = brokerage,
            stt = stt,
            exchangeCharges = exchangeCharges,
            gst = gst,
            sebi = sebi,
            stampDuty = stampDuty,
            dpCharges = dpCharges,
            total = total,
        )
    }
}
```

### 6.2 Charge Breakdown Data Class

```kotlin
data class ChargeBreakdown(
    val brokerage: Paisa,
    val stt: Paisa,
    val exchangeCharges: Paisa,
    val gst: Paisa,
    val sebi: Paisa,
    val stampDuty: Paisa,
    val dpCharges: Paisa,
    val total: Paisa,
) {
    /** Returns a list of (ChargeType, amount) for transaction insertion. */
    fun toTransactionEntries(): List<Pair<TransactionType, Paisa>> = listOfNotNull(
        (TransactionType.BROKERAGE_CHARGE to brokerage).takeIf { brokerage.value != 0L },
        TransactionType.STT_CHARGE to stt,
        TransactionType.EXCHANGE_CHARGE to exchangeCharges,
        TransactionType.GST_CHARGE to gst,
        TransactionType.SEBI_CHARGE to sebi,
        (TransactionType.STAMP_DUTY_CHARGE to stampDuty).takeIf { stampDuty.value != 0L },
        (TransactionType.DP_CHARGE to dpCharges).takeIf { dpCharges.value != 0L },
    )
}
```

### 6.3 Rounding Strategy

All charge calculations use **round-half-up** at each intermediate step. This matches Zerodha's contract note rounding behavior. The `applyBasisPoints` method implements this:

```
result = (value * basisPoints + 5000) / 10_000
```

The `+5000` provides half-up rounding for the division by `10_000`.

---

## 7. Target Sell Price Calculator

### 7.1 Algorithm

```
Pseudocode: calculateTargetSellPrice(holding, chargeRates)

  INPUT:
    holding.invested_amount     -- Total cost basis (paisa)
    holding.quantity            -- Number of shares
    holding.total_buy_charges   -- Sum of all buy charges (paisa)
    holding.profit_target       -- ProfitTarget (Percentage or Absolute)
    chargeRates                 -- Current charge rate snapshot

  COMPUTE:
    target_profit = profitTarget.computeTargetProfit(invested_amount)

    // Required sell value must cover: cost + profit + buy charges + estimated sell charges
    // Sell charges depend on sell value (circular dependency)
    // Solve iteratively:

    estimated_sell_value = invested_amount + target_profit + total_buy_charges

    FOR i IN 1..5:  // Max 5 iterations (converges in 2-3 typically)
      estimated_sell_charges = ChargeCalculator.calculateSellSideOnly(
        sellValue = estimated_sell_value,
        exchange = holding.exchange,
        rates = chargeRates
      ).total

      new_sell_value = invested_amount + target_profit + total_buy_charges + estimated_sell_charges

      IF abs(new_sell_value - estimated_sell_value) < Paisa(1):
        BREAK  // Converged
      estimated_sell_value = new_sell_value

    target_sell_price = estimated_sell_value / holding.quantity

    RETURN target_sell_price
```

**Why Iterative?** Sell charges (STT, exchange charges, GST) are a percentage of the sell value itself. The sell value must include the sell charges, creating a circular dependency. Iterative convergence resolves this. For typical charge rates (~0.03% combined), convergence occurs in 2 iterations.

---

## 8. Holdings Verifier

### 8.1 Algorithm

```kotlin
class HoldingsVerifier {

    data class VerificationResult(
        val isMatch: Boolean,
        val diffs: List<StockDiff>,
    )

    data class StockDiff(
        val stockCode: String,
        val localQuantity: Int,
        val remoteQuantity: Int,
        val difference: Int,  // remote - local
    )

    fun verify(
        remoteHoldings: List<RemoteHolding>,
        localHoldings: List<Holding>,
    ): VerificationResult {
        val remoteMap = remoteHoldings
            .filter { it.product == "CNC" }    // Equity delivery only
            .associateBy { it.tradingSymbol }

        val localMap = localHoldings
            .filter { it.quantity > 0 }
            .associateBy { it.stockCode }

        val allStocks = (remoteMap.keys + localMap.keys)
        val diffs = mutableListOf<StockDiff>()

        for (stock in allStocks) {
            val remoteQty = remoteMap[stock]?.quantity ?: 0
            val localQty = localMap[stock]?.quantity ?: 0

            if (remoteQty != localQty) {
                diffs.add(StockDiff(stock, localQty, remoteQty, remoteQty - localQty))
            }
        }

        return VerificationResult(
            isMatch = diffs.isEmpty(),
            diffs = diffs,
        )
    }
}
```

### 8.2 Edge Cases

| Scenario | Behavior |
|---|---|
| Remote has stocks not in local | Diff reported (localQty=0, remoteQty=N). User may need to import historical orders. |
| Local has stocks not in remote | Diff reported (localQty=N, remoteQty=0). Possible full sell not yet synced. |
| Remote returns non-CNC holdings (intraday, F&O) | Filtered out. Only `product == "CNC"` is compared. |
| Remote returns same stock on multiple exchanges | Each exchange-stock pair is treated independently (e.g., `INFY-NSE` vs `INFY-BSE`). |

---

## 9. P&L Calculator

### 9.1 Realized P&L Algorithm

```
Pseudocode: calculateRealizedPnl(startDate, endDate)

  // Method 1: From pre-aggregated cache (fast path)
  months = getMonthsBetween(startDate, endDate)
  cache_rows = pnlCacheDao.getForMonths(months)

  IF all months have cache entries AND cache entries are not stale:
    realized_pnl = SUM(cache_rows.realized_pnl_paisa)
    total_charges = SUM(cache_rows.total_buy_charges + cache_rows.total_sell_charges)
    invested_value = SUM(cache_rows.invested_value_paisa)
    RETURN PnlSummary(realized_pnl, total_charges, invested_value)

  // Method 2: From raw data (slow path, used for validation and uncached ranges)
  sell_orders = orderRepo.getSellOrdersInRange(startDate, endDate)

  total_sell_proceeds = Paisa.ZERO
  total_buy_cost_of_sold = Paisa.ZERO
  total_buy_charges_of_sold = Paisa.ZERO
  total_sell_charges = Paisa.ZERO

  FOR each sell_order IN sell_orders:
    total_sell_proceeds += sell_order.total_value

    // Attribute buy cost using FIFO from order_holdings_link
    buy_lots = orderHoldingsLinkRepo.getBuyLotsForStock(sell_order.stock_code)
    cost_basis = computeFifoCostBasis(buy_lots, sell_order.quantity)
    total_buy_cost_of_sold += cost_basis.totalCost
    total_buy_charges_of_sold += cost_basis.totalBuyCharges

    sell_charges = transactionRepo.getChargesForOrder(sell_order.zerodhaOrderId)
    total_sell_charges += sell_charges.total

  realized_pnl = total_sell_proceeds - total_buy_cost_of_sold
                  - total_buy_charges_of_sold - total_sell_charges

  RETURN PnlSummary(
    realizedPnl = realized_pnl,
    totalCharges = total_buy_charges_of_sold + total_sell_charges,
    investedValue = total invested buy value for period,
    pnlPercentage = (realized_pnl * 10000) / total_buy_cost_of_sold  // basis points
  )
```

### 9.2 FIFO Cost Basis Computation

```
Pseudocode: computeFifoCostBasis(buyLots, sellQuantity)

  // buyLots is ordered by trade_date ASC (FIFO)
  remaining_to_sell = sellQuantity
  total_cost = Paisa.ZERO
  total_charges = Paisa.ZERO

  FOR each lot IN buyLots:
    IF remaining_to_sell <= 0: BREAK

    qty_from_this_lot = MIN(lot.remaining_quantity, remaining_to_sell)
    cost_per_unit = lot.price
    total_cost += cost_per_unit * qty_from_this_lot
    total_charges += (lot.total_charges / lot.original_quantity) * qty_from_this_lot
    remaining_to_sell -= qty_from_this_lot

  RETURN CostBasis(totalCost = total_cost, totalBuyCharges = total_charges)
```

---

## 10. GTT Engine

### 10.1 GTT Placement/Update Logic

```
Pseudocode: GttEngine.processHoldings(affectedStockCodes, chargeRates)

  FOR each stockCode IN affectedStockCodes:
    holding = holdingRepo.getByStockCode(stockCode)
    IF holding == NULL OR holding.quantity == 0:
      // Stock fully sold — archive any existing GTT
      existingGtt = gttRepo.getActiveForStock(stockCode)
      IF existingGtt != NULL:
        gttRepo.archive(existingGtt.id)
      CONTINUE

    targetSellPrice = targetPriceCalculator.calculate(holding, chargeRates)
    existingGtt = gttRepo.getActiveForStock(stockCode)

    IF existingGtt == NULL:
      // CREATE new GTT
      gttId = kiteConnectRepo.placeGtt(
        stockCode = stockCode,
        triggerPrice = targetSellPrice,
        quantity = holding.quantity,
        triggerType = SINGLE,
        orderType = SELL
      )
      ON FAILURE:
        gttRepo.insert(GttRecord(status = PENDING_CREATION, ...))
        alertRepo.insert(PersistentAlert(GTT_CREATION_FAILED, WARNING))
        CONTINUE

      // VERIFY
      remoteGtt = kiteConnectRepo.fetchGtt(gttId)
      IF remoteGtt matches expected:
        gttRepo.insert(GttRecord(
          zerodhaGttId = gttId,
          status = ACTIVE,
          triggerPrice = targetSellPrice,
          quantity = holding.quantity,
          appCalculatedPrice = targetSellPrice
        ))
      ELSE:
        alertRepo.insert(PersistentAlert(GTT_VERIFICATION_FAILED))

    ELSE:
      // UPDATE existing GTT
      // First check for manual override
      remoteGtt = kiteConnectRepo.fetchGtt(existingGtt.zerodhaGttId)
      IF remoteGtt.triggerPrice != existingGtt.appCalculatedPrice:
        // Manual override detected
        gttRepo.update(existingGtt.copy(manualOverrideDetected = true))
        alertRepo.insert(PersistentAlert(GTT_MANUAL_OVERRIDE, WARNING, ...))
        EMIT AppEvent.GttManualOverrideDetected(stockCode)
        CONTINUE  // Don't overwrite; user must decide

      // No override — safe to update
      kiteConnectRepo.modifyGtt(
        gttId = existingGtt.zerodhaGttId,
        triggerPrice = targetSellPrice,
        quantity = holding.quantity
      )
      ON FAILURE:
        gttRepo.update(existingGtt.copy(status = PENDING_UPDATE))
        CONTINUE

      // VERIFY after update
      remoteGtt = kiteConnectRepo.fetchGtt(existingGtt.zerodhaGttId)
      IF remoteGtt matches expected:
        gttRepo.update(existingGtt.copy(
          triggerPrice = targetSellPrice,
          quantity = holding.quantity,
          appCalculatedPrice = targetSellPrice,
          status = ACTIVE,
          lastSyncedAt = now()
        ))
      ELSE:
        alertRepo.insert(PersistentAlert(GTT_VERIFICATION_FAILED))

    EMIT AppEvent.GttUpdated(stockCode)
```

---

## 11. Idempotency Safeguards

| Operation | Idempotency Mechanism |
|---|---|
| Order insertion | `zerodha_order_id UNIQUE` — duplicate inserts are ignored (`OnConflictStrategy.IGNORE`) |
| Charge calculation | Charges are calculated and stored at order insert time, inside the same transaction. Re-running the sync for the same orders is a no-op (orders already exist). |
| GTT creation | Before creating, check if active GTT exists for stock. If yes, update instead of create. |
| GTT modification | Fetch current GTT state before modifying. If already at target, skip modification. |
| Fund reconciliation | Auto-adjustment is idempotent: if balance already matches (within tolerance), adjustment is ₹0 (effectively no-op). |
| Gmail scan | `gmail_message_id UNIQUE` in `gmail_scan_cache` — same email is never processed twice. |
| CSV import | Duplicate detection by `zerodha_order_id` for orders, by `(type, amount, date)` for fund entries. |
| Backup upload | Backup file name includes timestamp. Re-uploading creates a new file, not overwrite. |

---

## 12. Concurrency Safety Rules

| Rule | Mechanism | Scope |
|---|---|---|
| Single-writer for order data | `Mutex("order_sync")` | SyncOrdersUseCase, ImportCsvUseCase |
| Single-writer for GTT data | `Mutex("gtt_operations")` | PlaceGttUseCase, UpdateProfitTargetUseCase |
| Single-writer for fund data | `Mutex("fund_operations")` | AddFundEntryUseCase, ReconcileFundUseCase |
| Serialized Kite API calls | `Semaphore(1)` on KiteConnectRepository | All Kite API methods |
| Atomic multi-table writes | Room `@Transaction` | All repository write methods touching >1 table |
| No concurrent backup + write | `Mutex("backup")` | CreateBackupUseCase, RestoreBackupUseCase |

```kotlin
class MutexRegistry @Inject constructor() {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    fun get(name: String): Mutex = mutexes.getOrPut(name) { Mutex() }
}

// Usage in UseCase:
class SyncOrdersUseCase @Inject constructor(
    private val mutexRegistry: MutexRegistry,
    // ...
) {
    suspend fun execute(): Result<SyncResult> {
        return mutexRegistry.get("order_sync").withLock {
            // ... sync logic
        }
    }
}
```

---

## 13. Failure Recovery Strategy

### 13.1 Per-Subsystem Recovery

| Subsystem | Failure | Recovery |
|---|---|---|
| Order Sync | API timeout | Worker retries with exponential backoff (30s, 60s, 120s). Max 3 retries. Next periodic run. |
| Order Sync | Holdings mismatch | CRITICAL alert. No data written. User must acknowledge. Could indicate external trades not tracked by app. |
| GTT Engine | GTT API fails | GTT marked `PENDING_CREATION` or `PENDING_UPDATE`. Retry on next sync. |
| GTT Engine | Verification mismatch | WARNING alert. Local record not updated. User can re-trigger from Holdings screen. |
| Fund Recon | API fails | Warning logged. Retry at next scheduled time. Balance unchanged. |
| Fund Recon | Exceeds tolerance | CRITICAL alert. No auto-adjustment. User investigates. |
| Charge Rates | API fails | Continue with last known rates. WARNING alert with last fetch date. |
| CSV Import | Invalid format | Entire import rejected. Error details shown to user. No partial writes. |
| Backup | Drive upload fails | Auto-fallback to local storage. User notified. |
| Backup | Restore corrupted file | Abort entirely. No writes. Existing data preserved. |
| Database | Migration failure | App enters read-only degraded mode. Prompts backup export. |
| Database | Corruption detected | SQLCipher integrity check fails → prompt user to restore from backup. |

### 13.2 Automatic Recovery Sequences

```
ORDER_SYNC_FAILED (Transient)
  └── Worker returns Result.retry()
        └── Exponential backoff: 30s → 60s → 120s
              └── If all retries fail:
                    └── Worker returns Result.failure()
                          └── sync_event_log entry with FAILED status
                          └── Amber warning banner on next app open
                          └── Next periodic run will attempt again

GTT_PENDING_CREATION
  └── Next ORDER_SYNC triggers GTT engine
        └── GTT engine detects PENDING_CREATION for stock
              └── Retries GTT placement
                    └── Success → status = ACTIVE
                    └── Failure → remains PENDING; re-attempted next cycle
```

---

## 14. Edge Case Matrix

### 14.1 Order Sync Edge Cases

| # | Scenario | Handling |
|---|---|---|
| E-ORD-01 | No orders today | Sync completes as SUCCESS with no changes. No verification triggered. |
| E-ORD-02 | Only intraday/F&O orders today | Filtered out. Treated as "no new equity delivery orders." |
| E-ORD-03 | Orders fetched but holdings fetch fails | Entire sync rolled back. No orders persisted. |
| E-ORD-04 | New buy order for stock already held | Holding quantity and avg price recalculated. GTT updated. |
| E-ORD-05 | Partial sell | Holding quantity reduced. GTT quantity updated. Cost basis adjusted via FIFO. |
| E-ORD-06 | Full sell (quantity → 0) | Holding retained with qty=0. GTT archived. P&L entry generated. |
| E-ORD-07 | Buy and sell of same stock on same day | Both orders processed. Net effect on holding computed. |
| E-ORD-08 | Charge rates not yet fetched | Orders persisted. Charge transactions deferred (placeholder). User prompted. |
| E-ORD-09 | Duplicate order ID in API response | Single insert (UNIQUE constraint). No error. |
| E-ORD-10 | Order with zero price (unlikely but defensive) | Rejected by `CHECK (price_paisa > 0)`. Logged as anomaly. |

### 14.2 Fund Management Edge Cases

| # | Scenario | Handling |
|---|---|---|
| E-FND-01 | Fund entry with exact duplicate (same type, amount, date) | Duplicate detector warns user before insert. User decides. |
| E-FND-02 | Gmail detects transaction matching manual entry | Flagged as potential duplicate in confirmation UI. |
| E-FND-03 | Reconciliation API returns 0 balance | Compared against local balance. If local is also 0, no action. Otherwise, standard mismatch handling. |
| E-FND-04 | User has no fund entries yet | Local balance = 0. Reconciliation compares 0 vs remote balance. |
| E-FND-05 | Auto-adjustment would be negative (remote < local) | Logged as negative `MISC_ADJUSTMENT` (withdrawal-like). Valid — may indicate charges or fees not tracked. |
| E-FND-06 | Tolerance set to 0 | Every non-zero discrepancy triggers an alert. No auto-adjustments. |

### 14.3 GTT Edge Cases

| # | Scenario | Handling |
|---|---|---|
| E-GTT-01 | GTT placement fails (rate limit) | Marked PENDING_CREATION. Retry next sync cycle. |
| E-GTT-02 | GTT already triggered/completed | Archived locally. No update attempted. New GTT created if holding still exists. |
| E-GTT-03 | User manually modifies GTT in Zerodha | Override detected on next fetch. User prompted to keep or revert. |
| E-GTT-04 | Holding sold to 0 but GTT still active | On next sync, GTT archived. If GTT was already triggered (sell executed), this is the expected flow. |
| E-GTT-05 | Profit target set to 0% | Target sell price = break-even (covering charges only). GTT placed at break-even price. Warning shown. |
| E-GTT-06 | Multiple buy orders for same stock in single sync | Holdings aggregated first, then single GTT created/updated with final qty and avg price. |
| E-GTT-07 | Kite Connect GTT API unavailable | Order sync proceeds normally. GTT deferred as PENDING. |

### 14.4 CSV Import Edge Cases

| # | Scenario | Handling |
|---|---|---|
| E-CSV-01 | File has BOM marker | Apache Commons CSV handles BOM transparently. |
| E-CSV-02 | File has different delimiter (tab, semicolon) | Rejected. Only comma-delimited accepted. Error specifies expected format. |
| E-CSV-03 | File has header row mismatch | Compared against expected headers. Mismatch → entire file rejected with specific column errors. |
| E-CSV-04 | Row has negative quantity or price | Row-level validation failure. Entire file rejected (for orders/funds). |
| E-CSV-05 | File from different Zerodha account | Account ID column checked against bound account. Mismatch → rejection with clear error. |
| E-CSV-06 | 50% of orders already exist (duplicates) | Duplicates silently skipped. Post-import summary: "150 imported, 150 skipped (duplicate)." |
| E-CSV-07 | File is empty (headers only) | Treated as valid with zero new records. Summary: "0 imported." |
| E-CSV-08 | File exceeds 100,000 rows | Processed in chunks (500 rows/batch). Memory-safe. No hard limit; user warned if > 10,000. |
