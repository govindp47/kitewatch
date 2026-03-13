package com.kitewatch.domain.engine

import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import kotlin.math.abs

/**
 * Instruction produced by [GttAutomationEngine] for a single instrument.
 * The caller is responsible for executing these instructions against the Kite Connect API.
 */
sealed class GttAction {
    /** No active GTT — create one at [targetPrice] for [quantity] shares. */
    data class CreateGtt(
        val stockCode: String,
        val quantity: Int,
        val targetPrice: Paisa,
    ) : GttAction()

    /** Existing app-managed GTT needs a price or quantity update. */
    data class UpdateGtt(
        val gttId: Long,
        val newQuantity: Int,
        val newTargetPrice: Paisa,
    ) : GttAction()

    /** Existing app-managed GTT is already at the correct price and quantity — do nothing. */
    data class NoAction(
        val stockCode: String,
    ) : GttAction()

    /**
     * GTT exists but [GttRecord.isAppManaged] is false — the user has manually set a different
     * trigger price ([zerodhaActualPrice]). Do not overwrite; surface for user resolution.
     */
    data class FlagManualOverride(
        val gttId: Long,
        val appTargetPrice: Paisa,
        val zerodhaActualPrice: Paisa,
    ) : GttAction()

    /** Holding quantity is 0 — archive the still-open GTT. */
    data class ArchiveGtt(
        val gttId: Long,
    ) : GttAction()
}

/**
 * Pure, stateless engine that classifies each holding into a [GttAction].
 *
 * Does NOT call any external API or perform I/O. The caller executes the returned actions.
 */
object GttAutomationEngine {
    private val DEFAULT_PROFIT_TARGET = ProfitTarget.Percentage(0)
    private const val PRICE_TOLERANCE_PAISA = 1L

    /**
     * Evaluate the required GTT action for every holding.
     *
     * @param holdings      Current computed holdings (including zero-quantity exits)
     * @param existingGtts  Active GTT records keyed by [GttRecord.stockCode]
     * @param chargeRates   Current charge rate snapshot for target price computation
     * @param profitTargets Profit target per stock code; defaults to 0% if absent
     * @return One [GttAction] per holding that requires attention
     */
    fun evaluate(
        holdings: List<ComputedHolding>,
        existingGtts: Map<String, GttRecord>,
        chargeRates: ChargeRateSnapshot,
        profitTargets: Map<String, ProfitTarget>,
    ): List<GttAction> =
        holdings.mapNotNull { holding ->
            val activeGtt = existingGtts[holding.stockCode]?.takeIf { it.status != GttStatus.ARCHIVED }
            if (holding.quantity == 0) {
                activeGtt?.let { GttAction.ArchiveGtt(it.gttId) }
            } else {
                evaluateActiveHolding(holding, activeGtt, chargeRates, profitTargets)
            }
        }

    private fun evaluateActiveHolding(
        holding: ComputedHolding,
        activeGtt: GttRecord?,
        chargeRates: ChargeRateSnapshot,
        profitTargets: Map<String, ProfitTarget>,
    ): GttAction {
        val profitTarget = profitTargets[holding.stockCode] ?: DEFAULT_PROFIT_TARGET
        val appTargetPrice =
            TargetPriceCalculator.compute(
                avgBuyPrice = holding.avgBuyPrice,
                quantity = holding.quantity,
                profitTarget = profitTarget,
                investedAmount = holding.investedAmount,
                buyCharges = holding.totalBuyCharges,
                chargeRates = chargeRates,
            )

        return when {
            activeGtt == null ->
                GttAction.CreateGtt(
                    stockCode = holding.stockCode,
                    quantity = holding.quantity,
                    targetPrice = appTargetPrice,
                )
            !activeGtt.isAppManaged ->
                GttAction.FlagManualOverride(
                    gttId = activeGtt.gttId,
                    appTargetPrice = appTargetPrice,
                    zerodhaActualPrice = activeGtt.triggerPrice,
                )
            else -> {
                // isAppManaged = true: compare price and quantity
                val priceDelta = abs(appTargetPrice.value - activeGtt.triggerPrice.value)
                val quantityChanged = holding.quantity != activeGtt.quantity
                if (priceDelta <= PRICE_TOLERANCE_PAISA && !quantityChanged) {
                    GttAction.NoAction(holding.stockCode)
                } else {
                    GttAction.UpdateGtt(
                        gttId = activeGtt.gttId,
                        newQuantity = holding.quantity,
                        newTargetPrice = appTargetPrice,
                    )
                }
            }
        }
    }
}
