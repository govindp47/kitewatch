package com.kitewatch.data.mapper

import com.kitewatch.database.entity.GttRecordEntity
import com.kitewatch.database.entity.HoldingEntity
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.network.kiteconnect.dto.FundBalanceDto
import com.kitewatch.network.kiteconnect.dto.GttDto
import com.kitewatch.network.kiteconnect.dto.HoldingDto
import com.kitewatch.network.kiteconnect.dto.OrderDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Maps an [OrderDto] from GET /orders to an [Order] domain model.
 *
 * Returns `null` when:
 *  - The order product is not "CNC" (equity delivery).
 *  - Any required field is absent or unparseable.
 */
fun OrderDto.toDomain(): Order? {
    if (!isValidCncOrder()) return null

    val zerodhaId = orderId!!
    val symbol = tradingSymbol!!
    val orderType = OrderType.valueOf(transactionType!!.uppercase())
    val qty = quantity!!
    val price = averagePrice!!
    val exchangeEnum = Exchange.valueOf(exchange!!.uppercase())
    val date = parseFillDate(fillTimestamp!!)!!

    val pricePaisa = Paisa.fromRupees(price)
    return Order(
        orderId = 0L, // assigned by Room on insert
        zerodhaOrderId = zerodhaId,
        stockCode = symbol,
        stockName = symbol, // Kite API does not return a display name in the orders endpoint
        orderType = orderType,
        quantity = qty,
        price = pricePaisa,
        totalValue = pricePaisa * qty,
        tradeDate = date,
        exchange = exchangeEnum,
        settlementId = null,
        source = OrderSource.SYNC,
    )
}

private fun OrderDto.isValidCncOrder(): Boolean {
    if (product?.uppercase() != "CNC") return false

    val qty = quantity?.takeIf { it > 0 }
    val price = averagePrice?.takeIf { it > 0.0 }
    val date = fillTimestamp?.let { parseFillDate(it) }

    return orderId != null &&
        tradingSymbol != null &&
        transactionType != null &&
        qty != null &&
        price != null &&
        exchange != null &&
        date != null
}

/**
 * Maps a [HoldingDto] from GET /portfolio/holdings to a [HoldingEntity].
 *
 * Returns `null` when required fields are absent.
 * Profit target, charges, and timestamps use entity defaults — they are populated
 * locally by the charge calculator, not from the API response.
 */
fun HoldingDto.toEntity(): HoldingEntity? {
    val symbol = tradingSymbol
    val qty = quantity
    val avgPrice = averagePrice?.takeIf { it >= 0.0 }

    if (symbol == null || qty == null || avgPrice == null) return null

    val avgPricePaisa = Paisa.fromRupees(avgPrice)
    val totalQty = qty + (t1Quantity ?: 0)
    val investedPaisa = avgPricePaisa * totalQty

    return HoldingEntity(
        id = 0L,
        stockCode = symbol,
        stockName = symbol,
        quantity = totalQty,
        avgBuyPricePaisa = avgPricePaisa.value,
        investedAmountPaisa = investedPaisa.value,
        totalBuyChargesPaisa = 0L,
        profitTargetType = "PERCENTAGE",
        profitTargetValue = 500,
        targetSellPricePaisa = investedPaisa.value, // placeholder; recalculated after charges
    )
}

/**
 * Extracts the available live balance from a [FundBalanceDto] as [Paisa].
 * Returns [Paisa.ZERO] when the equity segment or live balance is absent.
 */
fun FundBalanceDto.liveBalancePaisa(): Paisa {
    val rupees = equity?.available?.liveBalance ?: return Paisa.ZERO
    return Paisa.fromRupees(rupees)
}

/**
 * Maps a [GttDto] from GET /gtt to a [GttRecordEntity].
 *
 * Returns `null` when required fields are absent or the GTT has no orders.
 */
fun GttDto.toEntity(): GttRecordEntity? {
    if (!isValidGttOrder()) return null

    val gttId = id?.toLong()!!
    val symbol = condition?.tradingSymbol!!
    val triggerPrice = condition?.triggerValues?.firstOrNull()!!
    val order = orders?.firstOrNull()!!
    val qty = order?.quantity!!

    val domainStatus = mapGttStatus(status)

    val isArchived =
        if (
            domainStatus == "TRIGGERED" ||
            domainStatus == "CANCELLED" ||
            domainStatus == "EXPIRED"
        ) {
            1
        } else {
            0
        }

    return GttRecordEntity(
        id = 0L,
        zerodhaGttId = gttId,
        stockCode = symbol,
        triggerPricePaisa = Paisa.fromRupees(triggerPrice).value,
        quantity = qty,
        status = domainStatus,
        isAppManaged = 0, // externally fetched — not KiteWatch-originated by default
        manualOverrideDetected = 0,
        isArchived = isArchived,
    )
}

private fun GttDto.isValidGttOrder(): Boolean {
    val gttId = id?.toLong()
    val symbol = condition?.tradingSymbol
    val triggerPrice = condition?.triggerValues?.firstOrNull()
    val order = orders?.firstOrNull()
    val qty = order?.quantity

    return gttId != null &&
        symbol != null &&
        triggerPrice != null &&
        order != null &&
        qty != null
}

/** Normalises Zerodha GTT status strings to entity status column values. */
private fun mapGttStatus(status: String?): String =
    when (status?.lowercase()) {
        "active" -> "ACTIVE"
        "triggered" -> "TRIGGERED"
        "cancelled" -> "CANCELLED"
        "disabled" -> "CANCELLED"
        "expired" -> "EXPIRED"
        "rejected" -> "REJECTED"
        else -> "PENDING_CREATION"
    }

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun parseFillDate(fillTimestamp: String): LocalDate? =
    runCatching { LocalDate.parse(fillTimestamp, dateTimeFormatter) }.getOrNull()
        ?: runCatching { LocalDate.parse(fillTimestamp, dateOnlyFormatter) }.getOrNull()
