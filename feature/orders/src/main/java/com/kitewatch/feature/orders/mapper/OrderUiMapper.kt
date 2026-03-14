package com.kitewatch.feature.orders.mapper

import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderType
import com.kitewatch.feature.orders.model.OrderUiModel
import com.kitewatch.ui.formatter.CurrencyFormatter
import com.kitewatch.ui.formatter.DateFormatter

internal fun Order.toUiModel(chargesDisplay: String = "—"): OrderUiModel =
    OrderUiModel(
        orderId = orderId,
        date = DateFormatter.formatDisplay(tradeDate),
        stockCode = stockCode,
        stockName = stockName,
        type = orderType.name,
        isBuy = orderType == OrderType.BUY,
        quantity = quantity.toString(),
        price = CurrencyFormatter.format(price),
        totalValue = CurrencyFormatter.format(totalValue),
        charges = chargesDisplay,
    )
