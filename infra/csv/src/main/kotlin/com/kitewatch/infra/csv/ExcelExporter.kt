package com.kitewatch.infra.csv

import com.kitewatch.domain.model.Holding
import com.kitewatch.domain.model.Order
import com.kitewatch.domain.model.Paisa
import com.kitewatch.domain.model.Transaction
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Produces a multi-sheet .xlsx file from domain collections.
 * Sheet 1 "Orders", Sheet 2 "Holdings", Sheet 3 "Transactions".
 * No Android dependencies — pure JVM.
 */
object ExcelExporter {
    fun export(
        orders: List<Order>,
        holdings: List<Holding>,
        transactions: List<Transaction>,
    ): ByteArray {
        val workbook = XSSFWorkbook()
        val headerStyle =
            workbook.createCellStyle().apply {
                setFont(workbook.createFont().apply { bold = true })
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                borderBottom = BorderStyle.THIN
            }

        buildOrdersSheet(workbook, headerStyle, orders)
        buildHoldingsSheet(workbook, headerStyle, holdings)
        buildTransactionsSheet(workbook, headerStyle, transactions)

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    // ── Sheet 1: Orders ───────────────────────────────────────────────────────

    private fun buildOrdersSheet(
        workbook: XSSFWorkbook,
        headerStyle: org.apache.poi.ss.usermodel.CellStyle,
        orders: List<Order>,
    ) {
        val sheet = workbook.createSheet("Orders")
        val headers = listOf("Date", "Stock Code", "Stock Name", "Type", "Qty", "Price ₹", "Total ₹")
        writeHeaderRow(sheet, headers, headerStyle)

        orders.forEachIndexed { index, order ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(order.tradeDate.toString())
            row.createCell(1).setCellValue(order.stockCode)
            row.createCell(2).setCellValue(order.stockName)
            row.createCell(3).setCellValue(order.orderType.name)
            row.createCell(4).setCellValue(order.quantity.toDouble())
            row.createCell(5).setCellValue(order.price.toRupeesDouble())
            row.createCell(6).setCellValue(order.totalValue.toRupeesDouble())
        }

        autoSizeColumns(sheet, headers.size)
    }

    // ── Sheet 2: Holdings ─────────────────────────────────────────────────────

    private fun buildHoldingsSheet(
        workbook: XSSFWorkbook,
        headerStyle: org.apache.poi.ss.usermodel.CellStyle,
        holdings: List<Holding>,
    ) {
        val sheet = workbook.createSheet("Holdings")
        val headers =
            listOf(
                "Stock Code",
                "Stock Name",
                "Qty",
                "Avg Buy Price ₹",
                "Target Sell Price ₹",
                "Invested ₹",
            )
        writeHeaderRow(sheet, headers, headerStyle)

        holdings.forEachIndexed { index, holding ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(holding.stockCode)
            row.createCell(1).setCellValue(holding.stockName)
            row.createCell(2).setCellValue(holding.quantity.toDouble())
            row.createCell(3).setCellValue(holding.avgBuyPrice.toRupeesDouble())
            row.createCell(4).setCellValue(holding.targetSellPrice.toRupeesDouble())
            row.createCell(5).setCellValue(holding.investedAmount.toRupeesDouble())
        }

        autoSizeColumns(sheet, headers.size)
    }

    // ── Sheet 3: Transactions ─────────────────────────────────────────────────

    private fun buildTransactionsSheet(
        workbook: XSSFWorkbook,
        headerStyle: org.apache.poi.ss.usermodel.CellStyle,
        transactions: List<Transaction>,
    ) {
        val sheet = workbook.createSheet("Transactions")
        val headers = listOf("Date", "Type", "Stock Code", "Amount ₹", "Description")
        writeHeaderRow(sheet, headers, headerStyle)

        transactions.forEachIndexed { index, tx ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(tx.transactionDate.toString())
            row.createCell(1).setCellValue(tx.type.name)
            row.createCell(2).setCellValue(tx.stockCode ?: "")
            row.createCell(3).setCellValue(tx.amount.toRupeesDouble())
            row.createCell(4).setCellValue(tx.description)
        }

        autoSizeColumns(sheet, headers.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun writeHeaderRow(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        headers: List<String>,
        style: org.apache.poi.ss.usermodel.CellStyle,
    ) {
        val row = sheet.createRow(0)
        headers.forEachIndexed { col, label ->
            val cell = row.createCell(col)
            cell.setCellValue(label)
            cell.cellStyle = style
        }
    }

    private fun autoSizeColumns(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        columnCount: Int,
    ) {
        repeat(columnCount) { sheet.autoSizeColumn(it) }
    }

    private fun Paisa.toRupeesDouble(): Double =
        BigDecimal(value).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).toDouble()
}
