package com.kitewatch.data.mapper

import com.kitewatch.database.entity.ChargeRateEntity
import com.kitewatch.domain.model.ChargeRateSnapshot
import com.kitewatch.domain.model.Paisa
import java.time.Instant

/**
 * Assembles a [ChargeRateSnapshot] from a list of current [ChargeRateEntity] rows
 * (one per charge type, all with is_current = 1).
 *
 * [ChargeRateEntity.rateValue] stores milli-basis-points for BASIS_POINTS rates
 * and paisa for PAISA_FLAT / PAISA_PER_UNIT rates — the mapping is direct.
 *
 * @throws IllegalArgumentException if any required charge type is absent from the list.
 */
fun List<ChargeRateEntity>.toChargeRateSnapshot(): ChargeRateSnapshot {
    val byType = associateBy { it.rateType }

    fun rateValue(type: String): Int = requireNotNull(byType[type]) { "Missing charge rate for type: $type" }.rateValue

    val fetchedAt = byType.values.maxOf { it.fetchedAt }

    return ChargeRateSnapshot(
        brokerageDeliveryMilliBps = rateValue("BROKERAGE_DELIVERY"),
        sttBuyMilliBps = rateValue("STT_BUY"),
        sttSellMilliBps = rateValue("STT_SELL"),
        exchangeNseMilliBps = rateValue("EXCHANGE_NSE"),
        exchangeBseMilliBps = rateValue("EXCHANGE_BSE"),
        gstMilliBps = rateValue("GST"),
        sebiChargePerCrorePaisa = Paisa(rateValue("SEBI").toLong()),
        stampDutyBuyMilliBps = rateValue("STAMP_DUTY"),
        dpChargesPerScriptPaisa = Paisa(rateValue("DP_CHARGES_PER_SCRIPT").toLong()),
        fetchedAt = Instant.ofEpochMilli(fetchedAt),
    )
}

/**
 * Explodes a [ChargeRateSnapshot] into the list of [ChargeRateEntity] rows it represents.
 * [effectiveFrom] must be provided by the caller (ISO-8601 date string, e.g. "2024-01-01").
 */
fun ChargeRateSnapshot.toEntities(effectiveFrom: String): List<ChargeRateEntity> {
    val fetchedAtMillis = fetchedAt.toEpochMilli()
    return listOf(
        entity("BROKERAGE_DELIVERY", brokerageDeliveryMilliBps, "BASIS_POINTS", effectiveFrom, fetchedAtMillis),
        entity("STT_BUY", sttBuyMilliBps, "BASIS_POINTS", effectiveFrom, fetchedAtMillis),
        entity("STT_SELL", sttSellMilliBps, "BASIS_POINTS", effectiveFrom, fetchedAtMillis),
        entity("EXCHANGE_NSE", exchangeNseMilliBps, "BASIS_POINTS", effectiveFrom, fetchedAtMillis),
        entity("EXCHANGE_BSE", exchangeBseMilliBps, "BASIS_POINTS", effectiveFrom, fetchedAtMillis),
        entity("GST", gstMilliBps, "BASIS_POINTS", effectiveFrom, fetchedAtMillis),
        entity("SEBI", sebiChargePerCrorePaisa.value.toInt(), "PAISA_PER_UNIT", effectiveFrom, fetchedAtMillis),
        entity("STAMP_DUTY", stampDutyBuyMilliBps, "BASIS_POINTS", effectiveFrom, fetchedAtMillis),
        entity(
            "DP_CHARGES_PER_SCRIPT",
            dpChargesPerScriptPaisa.value.toInt(),
            "PAISA_FLAT",
            effectiveFrom,
            fetchedAtMillis,
        ),
    )
}

private fun entity(
    rateType: String,
    rateValue: Int,
    rateUnit: String,
    effectiveFrom: String,
    fetchedAt: Long,
): ChargeRateEntity =
    ChargeRateEntity(
        rateType = rateType,
        rateValue = rateValue,
        rateUnit = rateUnit,
        effectiveFrom = effectiveFrom,
        fetchedAt = fetchedAt,
        isCurrent = 1,
    )
