package com.kitewatch.infra.csv

import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvValidatorTest {
    private val today = LocalDate.of(2024, 6, 1)

    private fun order(
        zerodhaOrderId: String = "ORD1",
        tradeDate: LocalDate = LocalDate.of(2024, 1, 15),
    ) = Order(
        orderId = 0L,
        zerodhaOrderId = zerodhaOrderId,
        stockCode = "INFY",
        stockName = "INFY",
        orderType = OrderType.BUY,
        quantity = 5,
        price = Paisa(150_000L),
        totalValue = Paisa(750_000L),
        tradeDate = tradeDate,
        exchange = Exchange.NSE,
        settlementId = null,
        source = OrderSource.CSV_IMPORT,
    )

    @Test
    fun `valid orders with no duplicates returns empty error list`() {
        val orders = listOf(order("ORD1"), order("ORD2"), order("ORD3"))
        val errors = CsvValidator.validate(orders, emptySet(), today)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `future dated order produces an error`() {
        val futureDate = today.plusDays(1)
        val orders = listOf(order(tradeDate = futureDate))
        val errors = CsvValidator.validate(orders, emptySet(), today)
        assertEquals(1, errors.size)
        assertEquals("trade_date", errors.first().field)
    }

    @Test
    fun `order id matching existing database record produces an error`() {
        val orders = listOf(order("ORD_EXISTING"))
        val errors = CsvValidator.validate(orders, setOf("ORD_EXISTING"), today)
        assertEquals(1, errors.size)
        assertEquals("zerodha_order_id", errors.first().field)
    }

    @Test
    fun `intra-file duplicate order id produces an error on second occurrence`() {
        val orders = listOf(order("DUPLICATE"), order("DUPLICATE"))
        val errors = CsvValidator.validate(orders, emptySet(), today)
        assertEquals(1, errors.size)
        assertEquals(2, errors.first().rowNumber) // second row flagged
    }

    @Test
    fun `all errors collected at once not fail-fast`() {
        val orders =
            listOf(
                order("ORD1", tradeDate = today.plusDays(5)), // future date
                order("EXISTING"), // DB duplicate
                order("INTRA"), // intra-file dup row 1
                order("INTRA"), // intra-file dup row 2
            )
        val errors = CsvValidator.validate(orders, setOf("EXISTING"), today)
        // Expect: 1 future date + 1 DB dup + 1 intra-file dup = 3 errors
        assertEquals(3, errors.size)
    }

    @Test
    fun `today's date is accepted (not future)`() {
        val orders = listOf(order(tradeDate = today))
        val errors = CsvValidator.validate(orders, emptySet(), today)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `empty order list returns empty error list`() {
        val errors = CsvValidator.validate(emptyList(), emptySet(), today)
        assertTrue(errors.isEmpty())
    }
}
