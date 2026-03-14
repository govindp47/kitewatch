package com.kitewatch.data.mapper

import com.kitewatch.database.entity.OrderEntity
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import java.time.LocalDate

fun OrderEntity.toDomain(): Order =
    Order(
        orderId = id,
        zerodhaOrderId = zerodhaOrderId,
        stockCode = stockCode,
        stockName = stockName,
        orderType = OrderType.valueOf(orderType),
        quantity = quantity,
        price = Paisa(pricePaisa),
        totalValue = Paisa(totalValuePaisa),
        tradeDate = LocalDate.parse(tradeDate),
        exchange = Exchange.valueOf(exchange),
        settlementId = settlementId,
        source = OrderSource.valueOf(source),
    )

fun Order.toEntity(): OrderEntity =
    OrderEntity(
        id = orderId,
        zerodhaOrderId = zerodhaOrderId,
        stockCode = stockCode,
        stockName = stockName,
        orderType = orderType.name,
        quantity = quantity,
        pricePaisa = price.value,
        totalValuePaisa = totalValue.value,
        tradeDate = tradeDate.toString(),
        exchange = exchange.name,
        settlementId = settlementId,
        source = source.name,
    )
