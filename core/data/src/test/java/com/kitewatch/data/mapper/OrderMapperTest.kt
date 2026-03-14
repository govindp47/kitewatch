package com.kitewatch.data.mapper

import com.kitewatch.database.entity.OrderEntity
import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OrderMapperTest {
    private val entity =
        OrderEntity(
            id = 1L,
            zerodhaOrderId = "111111111",
            stockCode = "INFY",
            stockName = "Infosys Ltd",
            orderType = "BUY",
            quantity = 10,
            pricePaisa = 150000L,
            totalValuePaisa = 1500000L,
            tradeDate = "2024-01-15",
            exchange = "NSE",
            settlementId = "2024016",
            source = "SYNC",
            createdAt = 1_700_000_000_000L,
        )

    private val domain =
        Order(
            orderId = 1L,
            zerodhaOrderId = "111111111",
            stockCode = "INFY",
            stockName = "Infosys Ltd",
            orderType = OrderType.BUY,
            quantity = 10,
            price = Paisa(150000L),
            totalValue = Paisa(1500000L),
            tradeDate = LocalDate.of(2024, 1, 15),
            exchange = Exchange.NSE,
            settlementId = "2024016",
            source = OrderSource.SYNC,
        )

    @Test
    fun `entity toDomain maps all fields correctly`() {
        assertEquals(domain, entity.toDomain())
    }

    @Test
    fun `domain toEntity maps all fields correctly`() {
        val result = domain.toEntity()
        assertEquals(entity.id, result.id)
        assertEquals(entity.zerodhaOrderId, result.zerodhaOrderId)
        assertEquals(entity.stockCode, result.stockCode)
        assertEquals(entity.stockName, result.stockName)
        assertEquals(entity.orderType, result.orderType)
        assertEquals(entity.quantity, result.quantity)
        assertEquals(entity.pricePaisa, result.pricePaisa)
        assertEquals(entity.totalValuePaisa, result.totalValuePaisa)
        assertEquals(entity.tradeDate, result.tradeDate)
        assertEquals(entity.exchange, result.exchange)
        assertEquals(entity.settlementId, result.settlementId)
        assertEquals(entity.source, result.source)
    }

    @Test
    fun `round-trip entity-domain-entity preserves all fields`() {
        val roundTripped = entity.toDomain().toEntity()
        assertEquals(entity.zerodhaOrderId, roundTripped.zerodhaOrderId)
        assertEquals(entity.stockCode, roundTripped.stockCode)
        assertEquals(entity.orderType, roundTripped.orderType)
        assertEquals(entity.pricePaisa, roundTripped.pricePaisa)
        assertEquals(entity.tradeDate, roundTripped.tradeDate)
        assertEquals(entity.exchange, roundTripped.exchange)
        assertEquals(entity.settlementId, roundTripped.settlementId)
        assertEquals(entity.source, roundTripped.source)
    }

    @Test
    fun `null settlementId preserved`() {
        val e = entity.copy(settlementId = null)
        assertEquals(null, e.toDomain().settlementId)
        assertEquals(null, e.toDomain().toEntity().settlementId)
    }

    @Test
    fun `SELL order maps correctly`() {
        val e = entity.copy(orderType = "SELL")
        assertEquals(OrderType.SELL, e.toDomain().orderType)
    }

    @Test
    fun `BSE exchange maps correctly`() {
        val e = entity.copy(exchange = "BSE")
        assertEquals(Exchange.BSE, e.toDomain().exchange)
    }

    @Test
    fun `CSV_IMPORT source maps correctly`() {
        val e = entity.copy(source = "CSV_IMPORT")
        assertEquals(OrderSource.CSV_IMPORT, e.toDomain().source)
    }
}
