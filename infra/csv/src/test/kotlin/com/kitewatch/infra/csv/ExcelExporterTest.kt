package com.kitewatch.infra.csv

import com.kitewatch.domain.model.Exchange
import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.OrderSource
import com.kitewatch.domain.model.OrderType
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.ProfitTarget
import com.kitewatch.domain.model.Transaction
import com.kitewatch.domain.model.TransactionSource
import com.kitewatch.domain.model.TransactionType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate

class ExcelExporterTest {
    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val order =
        Order(
            orderId = 1L,
            zerodhaOrderId = "ORD001",
            stockCode = "INFY",
            stockName = "Infosys",
            orderType = OrderType.BUY,
            quantity = 10,
            price = Paisa(150_000L),
            totalValue = Paisa(1_500_000L),
            tradeDate = LocalDate.of(2024, 1, 15),
            exchange = Exchange.NSE,
            settlementId = null,
            source = OrderSource.CSV_IMPORT,
        )

    private val holding =
        Holding(
            holdingId = 1L,
            stockCode = "INFY",
            stockName = "Infosys",
            quantity = 10,
            avgBuyPrice = Paisa(150_000L),
            investedAmount = Paisa(1_500_000L),
            totalBuyCharges = Paisa(5_000L),
            profitTarget = ProfitTarget.Percentage(500),
            targetSellPrice = Paisa(157_500L),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private val transaction =
        Transaction(
            transactionId = 1L,
            type = TransactionType.EQUITY_BUY,
            referenceId = "ORD001",
            stockCode = "INFY",
            amount = Paisa(1_500_000L),
            transactionDate = LocalDate.of(2024, 1, 15),
            description = "Buy INFY",
            source = TransactionSource.CSV_IMPORT,
        )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `export produces valid xlsx with three sheets`() {
        val bytes =
            ExcelExporter.export(
                orders = listOf(order),
                holdings = listOf(holding),
                transactions = listOf(transaction),
            )

        assertTrue("ByteArray must be non-empty", bytes.isNotEmpty())

        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        assertEquals(3, workbook.numberOfSheets)
        assertEquals("Orders", workbook.getSheetAt(0).sheetName)
        assertEquals("Holdings", workbook.getSheetAt(1).sheetName)
        assertEquals("Transactions", workbook.getSheetAt(2).sheetName)
        workbook.close()
    }

    @Test
    fun `Orders sheet has correct row count matching input`() {
        val orders = listOf(order, order.copy(zerodhaOrderId = "ORD002", orderId = 2L))
        val bytes = ExcelExporter.export(orders, emptyList(), emptyList())

        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        val sheet = workbook.getSheetAt(0)
        // Row 0 = header, rows 1..N = data
        assertEquals(orders.size + 1, sheet.physicalNumberOfRows)
        workbook.close()
    }

    @Test
    fun `Holdings sheet has correct row count matching input`() {
        val bytes = ExcelExporter.export(emptyList(), listOf(holding), emptyList())

        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        val sheet = workbook.getSheetAt(1)
        assertEquals(2, sheet.physicalNumberOfRows) // header + 1 data row
        workbook.close()
    }

    @Test
    fun `Transactions sheet has correct row count matching input`() {
        val bytes = ExcelExporter.export(emptyList(), emptyList(), listOf(transaction))

        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        val sheet = workbook.getSheetAt(2)
        assertEquals(2, sheet.physicalNumberOfRows) // header + 1 data row
        workbook.close()
    }

    @Test
    fun `header row cells are bold in all sheets`() {
        val bytes = ExcelExporter.export(listOf(order), listOf(holding), listOf(transaction))

        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        repeat(3) { sheetIndex ->
            val headerRow = workbook.getSheetAt(sheetIndex).getRow(0)
            val firstCell = headerRow.getCell(0)
            assertTrue(
                "Sheet $sheetIndex header must be bold",
                firstCell.cellStyle.font.bold,
            )
        }
        workbook.close()
    }

    @Test
    fun `Orders sheet headers match expected columns`() {
        val bytes = ExcelExporter.export(listOf(order), emptyList(), emptyList())

        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        val headerRow = workbook.getSheetAt(0).getRow(0)
        assertEquals("Date", headerRow.getCell(0).stringCellValue)
        assertEquals("Stock Code", headerRow.getCell(1).stringCellValue)
        assertEquals("Type", headerRow.getCell(3).stringCellValue)
        workbook.close()
    }

    @Test
    fun `empty input produces header-only sheets`() {
        val bytes = ExcelExporter.export(emptyList(), emptyList(), emptyList())

        val workbook = XSSFWorkbook(ByteArrayInputStream(bytes))
        repeat(3) { sheetIndex ->
            assertEquals(
                "Sheet $sheetIndex should have only header row",
                1,
                workbook.getSheetAt(sheetIndex).physicalNumberOfRows,
            )
        }
        workbook.close()
    }
}
