package com.kitewatch.infra.csv

import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.OrderType
import com.kitewatch.infra.csv.model.CsvFormat
import com.kitewatch.infra.csv.model.CsvParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvParserTest {
    // ── detect() ─────────────────────────────────────────────────────────────

    @Test
    fun `detect identifies KITE_TRADE_BOOK format`() {
        val header =
            "trade_date,tradingsymbol,exchange,segment,series," +
                "trade_type,quantity,price,order_id,trade_id,order_execution_time"
        assertEquals(CsvFormat.KITE_TRADE_BOOK, CsvParser.detect(header))
    }

    @Test
    fun `detect identifies KITE_ORDERS format`() {
        val header = "Date,Type,Instrument,Product,Qty,Avg. price,Status"
        assertEquals(CsvFormat.KITE_ORDERS, CsvParser.detect(header))
    }

    @Test
    fun `detect identifies KITEWATCH_CUSTOM format`() {
        val header = "date,stock_code,exchange,type,quantity,price,zerodha_order_id"
        assertEquals(CsvFormat.KITEWATCH_CUSTOM, CsvParser.detect(header))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `detect throws on unknown header`() {
        CsvParser.detect("foo,bar,baz")
    }

    // ── KITE_TRADE_BOOK ───────────────────────────────────────────────────────

    @Test
    fun `trade book parses 10 valid EQ rows as Success`() {
        val csv =
            buildString {
                appendLine(
                    "trade_date,tradingsymbol,exchange,segment,series," +
                        "trade_type,quantity,price,order_id,trade_id,order_execution_time",
                )
                repeat(10) { i ->
                    appendLine(
                        "2024-01-${"%02d".format(
                            i + 1,
                        )},INFY,NSE,NSE,EQ,BUY,5,1500.00,ORD$i,TRD$i,2024-01-${"%02d".format(
                            i + 1,
                        )} 09:15:00",
                    )
                }
            }
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_TRADE_BOOK)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(10, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `trade book excludes non-EQ rows without generating errors`() {
        val csv =
            """
            trade_date,tradingsymbol,exchange,segment,series,trade_type,quantity,price,order_id,trade_id,order_execution_time
            2024-01-01,INFY,NSE,NSE,EQ,BUY,5,1500.00,ORD1,TRD1,2024-01-01 09:15:00
            2024-01-02,NIFTY,NSE,NFO,FO,BUY,1,200.00,ORD2,TRD2,2024-01-02 09:15:00
            2024-01-03,RELIANCE,NSE,NSE,EQ,SELL,3,2800.00,ORD3,TRD3,2024-01-03 09:15:00
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_TRADE_BOOK)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(2, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `trade book returns ValidationFailure with all errors for invalid rows`() {
        val csv =
            """
            trade_date,tradingsymbol,exchange,segment,series,trade_type,quantity,price,order_id,trade_id,order_execution_time
            2024-01-01,INFY,NSE,NSE,EQ,BUY,-5,1500.00,ORD1,TRD1,2024-01-01 09:15:00
            INVALID-DATE,RELIANCE,NSE,NSE,EQ,SELL,3,2800.00,ORD2,TRD2,INVALID 09:15:00
            2024-01-03,HDFC,NSE,NSE,EQ,BUY,2,1800.00,ORD3,TRD3,2024-01-03 09:15:00
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_TRADE_BOOK)
        assertTrue(result is CsvParseResult.ValidationFailure)
        val errors = (result as CsvParseResult.ValidationFailure).errors
        // Row 1: negative quantity; Row 2: invalid date — should have at least 2 errors
        assertTrue("Expected at least 2 errors, got ${errors.size}", errors.size >= 2)
    }

    @Test
    fun `trade book correctly maps order fields`() {
        val csv =
            """
            trade_date,tradingsymbol,exchange,segment,series,trade_type,quantity,price,order_id,trade_id,order_execution_time
            2024-03-15,INFY,NSE,NSE,EQ,BUY,10,1500.50,ORDER123,TRADE456,2024-03-15 10:00:00
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_TRADE_BOOK) as CsvParseResult.Success
        val order = result.orders.single()
        assertEquals("INFY", order.stockCode)
        assertEquals(Exchange.NSE, order.exchange)
        assertEquals(OrderType.BUY, order.orderType)
        assertEquals(10, order.quantity)
        assertEquals("ORDER123", order.zerodhaOrderId)
    }

    // ── KITE_ORDERS ───────────────────────────────────────────────────────────

    @Test
    fun `kite orders excludes non-CNC rows without errors`() {
        val csv =
            """
            Date,Type,Instrument,Product,Qty,Avg. price,Status
            2024-01-01,BUY,INFY,CNC,5,1500.00,COMPLETE
            2024-01-02,BUY,RELIANCE,MIS,10,2500.00,COMPLETE
            2024-01-03,SELL,HDFC,CNC,2,1800.00,COMPLETE
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_ORDERS)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(2, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `kite orders excludes non-COMPLETE status rows`() {
        val csv =
            """
            Date,Type,Instrument,Product,Qty,Avg. price,Status
            2024-01-01,BUY,INFY,CNC,5,1500.00,COMPLETE
            2024-01-02,BUY,RELIANCE,CNC,10,2500.00,REJECTED
            2024-01-03,SELL,HDFC,CNC,2,1800.00,CANCELLED
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_ORDERS)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(1, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `kite orders parses 10 valid CNC COMPLETE rows`() {
        val csv =
            buildString {
                appendLine("Date,Type,Instrument,Product,Qty,Avg. price,Status")
                repeat(10) { i ->
                    appendLine("2024-01-${"%02d".format(i + 1)},BUY,INFY,CNC,5,1500.00,COMPLETE")
                }
            }
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_ORDERS)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(10, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `kite orders ValidationFailure lists all errors`() {
        val csv =
            """
            Date,Type,Instrument,Product,Qty,Avg. price,Status
            BADDATE,BUY,INFY,CNC,5,1500.00,COMPLETE
            2024-01-02,BUY,RELIANCE,CNC,-3,2500.00,COMPLETE
            2024-01-03,SELL,HDFC,CNC,2,1800.00,COMPLETE
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITE_ORDERS)
        assertTrue(result is CsvParseResult.ValidationFailure)
        val errors = (result as CsvParseResult.ValidationFailure).errors
        assertTrue("Expected at least 2 errors, got ${errors.size}", errors.size >= 2)
    }

    // ── KITEWATCH_CUSTOM ──────────────────────────────────────────────────────

    @Test
    fun `kitewatch custom parses 10 valid rows as Success`() {
        val csv =
            buildString {
                appendLine("date,stock_code,exchange,type,quantity,price,zerodha_order_id")
                repeat(10) { i ->
                    appendLine("2024-01-${"%02d".format(i + 1)},INFY,NSE,BUY,5,1500.00,ORD$i")
                }
            }
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITEWATCH_CUSTOM)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(10, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `kitewatch custom ValidationFailure collects all errors`() {
        val csv =
            """
            date,stock_code,exchange,type,quantity,price,zerodha_order_id
            BADDATE,INFY,NSE,BUY,5,1500.00,ORD1
            2024-01-02,RELIANCE,NSE,BUY,-10,2500.00,ORD2
            2024-01-03,HDFC,NSE,BUY,2,1800.00,ORD3
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITEWATCH_CUSTOM)
        assertTrue(result is CsvParseResult.ValidationFailure)
        val errors = (result as CsvParseResult.ValidationFailure).errors
        assertTrue("Expected 2 errors (bad date + negative qty), got ${errors.size}", errors.size >= 2)
    }

    @Test
    fun `kitewatch custom maps SELL order type correctly`() {
        val csv =
            """
            date,stock_code,exchange,type,quantity,price,zerodha_order_id
            2024-03-15,RELIANCE,BSE,SELL,3,2800.00,ORD999
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITEWATCH_CUSTOM) as CsvParseResult.Success
        val order = result.orders.single()
        assertEquals(OrderType.SELL, order.orderType)
        assertEquals(Exchange.BSE, order.exchange)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `empty file after header returns empty Success`() {
        val csv = "date,stock_code,exchange,type,quantity,price,zerodha_order_id\n"
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITEWATCH_CUSTOM)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(0, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `completely empty file returns empty Success`() {
        val result = CsvParser.parse(ByteArrayInputStream(ByteArray(0)), CsvFormat.KITEWATCH_CUSTOM)
        assertTrue(result is CsvParseResult.Success)
        assertEquals(0, (result as CsvParseResult.Success).orders.size)
    }

    @Test
    fun `missing required column in header returns header ValidationFailure`() {
        // Missing price column
        val csv = "date,stock_code,exchange,type,quantity,zerodha_order_id\n2024-01-01,INFY,NSE,BUY,5,ORD1"
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITEWATCH_CUSTOM)
        assertTrue(result is CsvParseResult.ValidationFailure)
        val errors = (result as CsvParseResult.ValidationFailure).errors
        assertEquals(0, errors.first().rowNumber) // header-level error
    }

    @Test
    fun `totalValue is price times quantity`() {
        val csv =
            """
            date,stock_code,exchange,type,quantity,price,zerodha_order_id
            2024-01-01,INFY,NSE,BUY,4,100.00,ORD1
            """.trimIndent()
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITEWATCH_CUSTOM) as CsvParseResult.Success
        val order = result.orders.single()
        // 400 rupees = 40000 paisa
        assertEquals(40_000L, order.totalValue.value)
    }

    @Test
    fun `splitCsvLine handles quoted fields with commas`() {
        val line = """"hello, world",123,"foo""bar",baz"""
        val parts = CsvParser.splitCsvLine(line)
        assertEquals(4, parts.size)
        assertEquals("hello, world", parts[0])
        assertEquals("123", parts[1])
        assertEquals("foo\"bar", parts[2])
        assertEquals("baz", parts[3])
    }

    @Test
    fun `BOM stripped from first line`() {
        val csv =
            "\uFEFFdate,stock_code,exchange,type,quantity,price,zerodha_order_id\n" +
                "2024-01-01,INFY,NSE,BUY,5,100.00,ORD1"
        val result = CsvParser.parse(csv.byteInputStream(), CsvFormat.KITEWATCH_CUSTOM)
        assertTrue(result is CsvParseResult.Success)
    }
}
