package com.kitewatch.data.mapper

import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.network.kiteconnect.dto.FundAvailableDto
import com.kitewatch.network.kiteconnect.dto.FundBalanceDto
import com.kitewatch.network.kiteconnect.dto.FundSegmentDto
import com.kitewatch.network.kiteconnect.dto.GttConditionDto
import com.kitewatch.network.kiteconnect.dto.GttDto
import com.kitewatch.network.kiteconnect.dto.GttOrderDto
import com.kitewatch.network.kiteconnect.dto.HoldingDto
import com.kitewatch.network.kiteconnect.dto.OrderDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ApiDtoMapperTest {
    // ─── OrderDto.toDomain() ─────────────────────────────────────────────────

    private val cncOrderDto =
        OrderDto(
            orderId = "111111111",
            tradingSymbol = "INFY",
            transactionType = "BUY",
            quantity = 10,
            averagePrice = 1500.0,
            status = "COMPLETE",
            product = "CNC",
            exchange = "NSE",
            fillTimestamp = "2024-01-15 10:30:00",
        )

    @Test
    fun `CNC order maps to domain Order`() {
        val order = cncOrderDto.toDomain()
        assertNotNull(order)
        assertEquals("111111111", order!!.zerodhaOrderId)
        assertEquals("INFY", order.stockCode)
        assertEquals(OrderType.BUY, order.orderType)
        assertEquals(10, order.quantity)
        assertEquals(Paisa.fromRupees(1500.0), order.price)
        assertEquals(Paisa.fromRupees(1500.0) * 10, order.totalValue)
        assertEquals(LocalDate.of(2024, 1, 15), order.tradeDate)
        assertEquals(Exchange.NSE, order.exchange)
        assertEquals(OrderSource.SYNC, order.source)
        assertNull(order.settlementId)
    }

    @Test
    fun `non-CNC product returns null`() {
        assertNull(cncOrderDto.copy(product = "MIS").toDomain())
        assertNull(cncOrderDto.copy(product = "NRML").toDomain())
        assertNull(cncOrderDto.copy(product = null).toDomain())
    }

    @Test
    fun `null orderId returns null`() {
        assertNull(cncOrderDto.copy(orderId = null).toDomain())
    }

    @Test
    fun `null tradingSymbol returns null`() {
        assertNull(cncOrderDto.copy(tradingSymbol = null).toDomain())
    }

    @Test
    fun `zero quantity returns null`() {
        assertNull(cncOrderDto.copy(quantity = 0).toDomain())
    }

    @Test
    fun `null fillTimestamp returns null`() {
        assertNull(cncOrderDto.copy(fillTimestamp = null).toDomain())
    }

    @Test
    fun `zero averagePrice returns null`() {
        assertNull(cncOrderDto.copy(averagePrice = 0.0).toDomain())
    }

    @Test
    fun `SELL order maps OrderType correctly`() {
        val order = cncOrderDto.copy(transactionType = "SELL").toDomain()
        assertEquals(OrderType.SELL, order!!.orderType)
    }

    @Test
    fun `BSE exchange maps correctly`() {
        val order = cncOrderDto.copy(exchange = "BSE").toDomain()
        assertEquals(Exchange.BSE, order!!.exchange)
    }

    // ─── HoldingDto.toEntity() ───────────────────────────────────────────────

    private val holdingDto =
        HoldingDto(
            tradingSymbol = "INFY",
            quantity = 10,
            averagePrice = 1500.0,
            t1Quantity = 2,
            exchange = "NSE",
            isin = "INE009A01021",
        )

    @Test
    fun `holding dto maps stock code and quantity (including T+1)`() {
        val entity = holdingDto.toEntity()
        assertNotNull(entity)
        assertEquals("INFY", entity!!.stockCode)
        assertEquals(12, entity.quantity) // 10 + 2 T+1
        assertEquals(Paisa.fromRupees(1500.0).value, entity.avgBuyPricePaisa)
    }

    @Test
    fun `holding dto null T1 quantity treated as zero`() {
        val entity = holdingDto.copy(t1Quantity = null).toEntity()
        assertEquals(10, entity!!.quantity)
    }

    @Test
    fun `holding dto null tradingSymbol returns null`() {
        assertNull(holdingDto.copy(tradingSymbol = null).toEntity())
    }

    @Test
    fun `holding dto null quantity returns null`() {
        assertNull(holdingDto.copy(quantity = null).toEntity())
    }

    @Test
    fun `holding dto default profit target is PERCENTAGE 500`() {
        val entity = holdingDto.toEntity()!!
        assertEquals("PERCENTAGE", entity.profitTargetType)
        assertEquals(500, entity.profitTargetValue)
    }

    // ─── FundBalanceDto.liveBalancePaisa() ───────────────────────────────────

    @Test
    fun `fund balance dto extracts live balance as Paisa`() {
        val dto =
            FundBalanceDto(
                equity =
                    FundSegmentDto(
                        available = FundAvailableDto(liveBalance = 100000.0, openingBalance = 95000.0),
                        net = 100000.0,
                    ),
            )
        assertEquals(Paisa.fromRupees(100000.0), dto.liveBalancePaisa())
    }

    @Test
    fun `null equity segment returns ZERO`() {
        assertEquals(Paisa.ZERO, FundBalanceDto(equity = null).liveBalancePaisa())
    }

    @Test
    fun `null liveBalance returns ZERO`() {
        val dto =
            FundBalanceDto(
                equity =
                    FundSegmentDto(
                        available = FundAvailableDto(liveBalance = null, openingBalance = null),
                        net = null,
                    ),
            )
        assertEquals(Paisa.ZERO, dto.liveBalancePaisa())
    }

    // ─── GttDto.toEntity() ───────────────────────────────────────────────────

    private val gttDto =
        GttDto(
            id = 12345,
            type = "single",
            status = "active",
            condition =
                GttConditionDto(
                    tradingSymbol = "INFY",
                    triggerValues = listOf(1750.0),
                    exchange = "NSE",
                ),
            orders =
                listOf(
                    GttOrderDto(
                        quantity = 10,
                        transactionType = "SELL",
                        product = "CNC",
                        orderType = "LIMIT",
                        price = 1750.0,
                    ),
                ),
        )

    @Test
    fun `GTT dto maps to entity with ACTIVE status`() {
        val entity = gttDto.toEntity()
        assertNotNull(entity)
        assertEquals(12345L, entity!!.zerodhaGttId)
        assertEquals("INFY", entity.stockCode)
        assertEquals(Paisa.fromRupees(1750.0).value, entity.triggerPricePaisa)
        assertEquals(10, entity.quantity)
        assertEquals("ACTIVE", entity.status)
        assertEquals(0, entity.isAppManaged)
    }

    @Test
    fun `triggered GTT sets isArchived=1`() {
        val entity = gttDto.copy(status = "triggered").toEntity()
        assertEquals(1, entity!!.isArchived)
        assertEquals("TRIGGERED", entity.status)
    }

    @Test
    fun `cancelled GTT maps to CANCELLED status`() {
        val entity = gttDto.copy(status = "cancelled").toEntity()
        assertEquals("CANCELLED", entity!!.status)
    }

    @Test
    fun `null GTT id returns null`() {
        assertNull(gttDto.copy(id = null).toEntity())
    }

    @Test
    fun `null condition returns null`() {
        assertNull(gttDto.copy(condition = null).toEntity())
    }

    @Test
    fun `empty orders list returns null`() {
        assertNull(gttDto.copy(orders = emptyList()).toEntity())
    }
}
