package com.kitewatch.infra.backup.mapper

import com.kitewatch.database.entity.ChargeRateEntity
import com.kitewatch.database.entity.FundEntryEntity
import com.kitewatch.database.entity.GmailFilterEntity
import com.kitewatch.database.entity.GmailScanCacheEntity
import com.kitewatch.database.entity.GttRecordEntity
import com.kitewatch.database.entity.HoldingEntity
import com.kitewatch.database.entity.OrderEntity
import com.kitewatch.database.entity.OrderHoldingEntity
import com.kitewatch.database.entity.PersistentAlertEntity
import com.kitewatch.database.entity.PnlMonthlyCacheEntity
import com.kitewatch.database.entity.TransactionEntity
import com.kitewatch.infra.backup.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupProtoMappersTest {
    // Fixed timestamp for deterministic tests
    private val epochMs = 1_742_000_000_000L // 2025-03-14T..

    // -----------------------------------------------------------------------
    // OrderEntity
    // -----------------------------------------------------------------------

    @Test
    fun `OrderEntity round-trip preserves all fields`() {
        val entity =
            OrderEntity(
                id = 42L,
                zerodhaOrderId = "ORD001",
                stockCode = "INFY",
                stockName = "Infosys Ltd",
                exchange = "NSE",
                orderType = "BUY",
                quantity = 10,
                pricePaisa = 150000L,
                totalValuePaisa = 1500000L,
                tradeDate = "2026-03-15",
                settlementId = "2026315",
                source = "SYNC",
                createdAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.zerodhaOrderId, restored.zerodhaOrderId)
        assertEquals(entity.stockCode, restored.stockCode)
        assertEquals(entity.stockName, restored.stockName)
        assertEquals(entity.exchange, restored.exchange)
        assertEquals(entity.orderType, restored.orderType)
        assertEquals(entity.quantity, restored.quantity)
        assertEquals(entity.pricePaisa, restored.pricePaisa)
        assertEquals(entity.totalValuePaisa, restored.totalValuePaisa)
        assertEquals(entity.tradeDate, restored.tradeDate)
        assertEquals(entity.settlementId, restored.settlementId)
        assertEquals(entity.source, restored.source)
        assertEquals(entity.createdAt, restored.createdAt)
    }

    @Test
    fun `OrderEntity nullable settlementId survives round-trip as null`() {
        val entity =
            OrderEntity(
                id = 1L,
                zerodhaOrderId = "X",
                stockCode = "X",
                stockName = "X",
                exchange = "NSE",
                orderType = "BUY",
                quantity = 1,
                pricePaisa = 1L,
                totalValuePaisa = 1L,
                tradeDate = "2026-01-01",
                settlementId = null,
                createdAt = epochMs,
            )
        assertNull(entity.toProto().toEntity().settlementId)
    }

    // -----------------------------------------------------------------------
    // HoldingEntity
    // -----------------------------------------------------------------------

    @Test
    fun `HoldingEntity round-trip preserves all fields`() {
        val entity =
            HoldingEntity(
                id = 1L,
                stockCode = "INFY",
                stockName = "Infosys Ltd",
                quantity = 10,
                avgBuyPricePaisa = 150000L,
                investedAmountPaisa = 1500000L,
                totalBuyChargesPaisa = 3000L,
                profitTargetType = "PERCENTAGE",
                profitTargetValue = 500,
                targetSellPricePaisa = 157500L,
                createdAt = epochMs,
                updatedAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.stockCode, restored.stockCode)
        assertEquals(entity.stockName, restored.stockName)
        assertEquals(entity.quantity, restored.quantity)
        assertEquals(entity.avgBuyPricePaisa, restored.avgBuyPricePaisa)
        assertEquals(entity.investedAmountPaisa, restored.investedAmountPaisa)
        assertEquals(entity.totalBuyChargesPaisa, restored.totalBuyChargesPaisa)
        assertEquals(entity.profitTargetType, restored.profitTargetType)
        assertEquals(entity.profitTargetValue, restored.profitTargetValue)
        assertEquals(entity.targetSellPricePaisa, restored.targetSellPricePaisa)
        assertEquals(entity.createdAt, restored.createdAt)
        assertEquals(entity.updatedAt, restored.updatedAt)
    }

    // -----------------------------------------------------------------------
    // TransactionEntity
    // -----------------------------------------------------------------------

    @Test
    fun `TransactionEntity round-trip preserves all fields including nullables`() {
        val entity =
            TransactionEntity(
                id = 5L,
                type = "EQUITY_BUY",
                subType = "AUTO_RECONCILIATION",
                referenceId = "ORD001",
                referenceType = "ORDER",
                stockCode = "INFY",
                amountPaisa = -1500000L,
                runningFundBalancePaisa = 8500000L,
                description = "Buy INFY 10@1500",
                transactionDate = "2026-03-15",
                source = "SYNC",
                createdAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.type, restored.type)
        assertEquals(entity.subType, restored.subType)
        assertEquals(entity.referenceId, restored.referenceId)
        assertEquals(entity.referenceType, restored.referenceType)
        assertEquals(entity.stockCode, restored.stockCode)
        assertEquals(entity.amountPaisa, restored.amountPaisa)
        assertEquals(entity.runningFundBalancePaisa, restored.runningFundBalancePaisa)
        assertEquals(entity.description, restored.description)
        assertEquals(entity.transactionDate, restored.transactionDate)
        assertEquals(entity.source, restored.source)
        assertEquals(entity.createdAt, restored.createdAt)
    }

    @Test
    fun `TransactionEntity null optional fields survive round-trip as null`() {
        val entity =
            TransactionEntity(
                id = 1L,
                type = "FUND_ADDITION",
                amountPaisa = 1000000L,
                transactionDate = "2026-01-01",
                subType = null,
                referenceId = null,
                referenceType = null,
                stockCode = null,
                runningFundBalancePaisa = null,
                createdAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertNull(restored.subType)
        assertNull(restored.referenceId)
        assertNull(restored.referenceType)
        assertNull(restored.stockCode)
        assertNull(restored.runningFundBalancePaisa)
    }

    // -----------------------------------------------------------------------
    // FundEntryEntity
    // -----------------------------------------------------------------------

    @Test
    fun `FundEntryEntity round-trip preserves all fields`() {
        val entity =
            FundEntryEntity(
                id = 3L,
                entryType = "ADDITION",
                amountPaisa = 10000000L,
                entryDate = "2026-03-01",
                note = "Initial deposit",
                isConfirmed = 1,
                gmailMessageId = "msg123",
                reconciliationId = "REC001",
                createdAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.entryType, restored.entryType)
        assertEquals(entity.amountPaisa, restored.amountPaisa)
        assertEquals(entity.entryDate, restored.entryDate)
        assertEquals(entity.note, restored.note)
        assertEquals(entity.gmailMessageId, restored.gmailMessageId)
        assertEquals(entity.reconciliationId, restored.reconciliationId)
        assertEquals(entity.createdAt, restored.createdAt)
    }

    @Test
    fun `FundEntryEntity null optional fields survive round-trip as null`() {
        val entity =
            FundEntryEntity(
                id = 1L,
                entryType = "ADDITION",
                amountPaisa = 1000L,
                entryDate = "2026-01-01",
                note = null,
                gmailMessageId = null,
                reconciliationId = null,
                createdAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertNull(restored.note)
        assertNull(restored.gmailMessageId)
        assertNull(restored.reconciliationId)
    }

    // -----------------------------------------------------------------------
    // ChargeRateEntity
    // -----------------------------------------------------------------------

    @Test
    fun `ChargeRateEntity round-trip preserves all fields`() {
        val entity =
            ChargeRateEntity(
                id = 1L,
                rateType = "STT_BUY",
                rateValue = 10,
                rateUnit = "BASIS_POINTS",
                effectiveFrom = "2024-01-01",
                fetchedAt = epochMs,
                isCurrent = 1,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.rateType, restored.rateType)
        assertEquals(entity.rateValue, restored.rateValue)
        assertEquals(entity.rateUnit, restored.rateUnit)
        assertEquals(entity.effectiveFrom, restored.effectiveFrom)
        assertEquals(entity.fetchedAt, restored.fetchedAt)
        assertEquals(entity.isCurrent, restored.isCurrent)
    }

    @Test
    fun `ChargeRateEntity isCurrent=0 survives round-trip`() {
        val entity =
            ChargeRateEntity(
                id = 2L,
                rateType = "STT_SELL",
                rateValue = 10,
                rateUnit = "BASIS_POINTS",
                effectiveFrom = "2023-01-01",
                fetchedAt = epochMs,
                isCurrent = 0,
            )
        assertEquals(0, entity.toProto().toEntity().isCurrent)
    }

    // -----------------------------------------------------------------------
    // GttRecordEntity
    // -----------------------------------------------------------------------

    @Test
    fun `GttRecordEntity round-trip preserves all fields`() {
        val entity =
            GttRecordEntity(
                id = 1L,
                zerodhaGttId = 98765L,
                stockCode = "INFY",
                triggerPricePaisa = 157500L,
                quantity = 10,
                status = "ACTIVE",
                isAppManaged = 1,
                appCalculatedPrice = 157500L,
                manualOverrideDetected = 0,
                holdingId = 1L,
                lastSyncedAt = epochMs,
                isArchived = 0,
                createdAt = epochMs,
                updatedAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.zerodhaGttId, restored.zerodhaGttId)
        assertEquals(entity.stockCode, restored.stockCode)
        assertEquals(entity.triggerPricePaisa, restored.triggerPricePaisa)
        assertEquals(entity.quantity, restored.quantity)
        assertEquals(entity.status, restored.status)
        assertEquals(entity.isAppManaged, restored.isAppManaged)
        assertEquals(entity.appCalculatedPrice, restored.appCalculatedPrice)
        assertEquals(entity.manualOverrideDetected, restored.manualOverrideDetected)
        assertEquals(entity.holdingId, restored.holdingId)
        assertEquals(entity.lastSyncedAt, restored.lastSyncedAt)
        assertEquals(entity.isArchived, restored.isArchived)
        assertEquals(entity.createdAt, restored.createdAt)
        assertEquals(entity.updatedAt, restored.updatedAt)
    }

    @Test
    fun `GttRecordEntity null optional fields survive round-trip as null`() {
        val entity =
            GttRecordEntity(
                id = 1L,
                stockCode = "INFY",
                triggerPricePaisa = 100L,
                quantity = 5,
                createdAt = epochMs,
                updatedAt = epochMs,
                zerodhaGttId = null,
                appCalculatedPrice = null,
                holdingId = null,
                lastSyncedAt = null,
            )
        val restored = entity.toProto().toEntity()
        assertNull(restored.zerodhaGttId)
        assertNull(restored.appCalculatedPrice)
        assertNull(restored.holdingId)
        assertNull(restored.lastSyncedAt)
    }

    // -----------------------------------------------------------------------
    // OrderHoldingEntity
    // -----------------------------------------------------------------------

    @Test
    fun `OrderHoldingEntity round-trip preserves all fields`() {
        val entity = OrderHoldingEntity(orderId = 10L, holdingId = 3L, quantity = 7)
        val restored = entity.toProto().toEntity()
        assertEquals(entity.orderId, restored.orderId)
        assertEquals(entity.holdingId, restored.holdingId)
        assertEquals(entity.quantity, restored.quantity)
    }

    // -----------------------------------------------------------------------
    // PnlMonthlyCacheEntity
    // -----------------------------------------------------------------------

    @Test
    fun `PnlMonthlyCacheEntity round-trip preserves all fields`() {
        val entity =
            PnlMonthlyCacheEntity(
                id = 1L,
                yearMonth = "2026-03",
                totalSellValuePaisa = 2000000L,
                totalBuyCostSoldPaisa = 1800000L,
                totalBuyChargesPaisa = 5000L,
                totalSellChargesPaisa = 4500L,
                realizedPnlPaisa = 190500L,
                investedValuePaisa = 1800000L,
                orderCount = 2,
                lastUpdatedAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.yearMonth, restored.yearMonth)
        assertEquals(entity.totalSellValuePaisa, restored.totalSellValuePaisa)
        assertEquals(entity.totalBuyCostSoldPaisa, restored.totalBuyCostSoldPaisa)
        assertEquals(entity.totalBuyChargesPaisa, restored.totalBuyChargesPaisa)
        assertEquals(entity.totalSellChargesPaisa, restored.totalSellChargesPaisa)
        assertEquals(entity.realizedPnlPaisa, restored.realizedPnlPaisa)
        assertEquals(entity.investedValuePaisa, restored.investedValuePaisa)
        assertEquals(entity.orderCount, restored.orderCount)
        assertEquals(entity.lastUpdatedAt, restored.lastUpdatedAt)
    }

    // -----------------------------------------------------------------------
    // GmailScanCacheEntity
    // -----------------------------------------------------------------------

    @Test
    fun `GmailScanCacheEntity round-trip preserves all fields`() {
        val entity =
            GmailScanCacheEntity(
                id = 1L,
                gmailMessageId = "msg_abc",
                detectedType = "ADDITION",
                detectedAmountPaisa = 10000000L,
                emailDate = "2026-03-01",
                emailSubject = "Amount credited",
                emailSender = "noreply@zerodha.com",
                status = "CONFIRMED",
                linkedFundEntryId = 5L,
                scannedAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.gmailMessageId, restored.gmailMessageId)
        assertEquals(entity.detectedType, restored.detectedType)
        assertEquals(entity.detectedAmountPaisa, restored.detectedAmountPaisa)
        assertEquals(entity.emailDate, restored.emailDate)
        assertEquals(entity.emailSubject, restored.emailSubject)
        assertEquals(entity.emailSender, restored.emailSender)
        assertEquals(entity.status, restored.status)
        assertEquals(entity.linkedFundEntryId, restored.linkedFundEntryId)
        assertEquals(entity.scannedAt, restored.scannedAt)
    }

    @Test
    fun `GmailScanCacheEntity null optional fields survive round-trip as null`() {
        val entity =
            GmailScanCacheEntity(
                id = 1L,
                gmailMessageId = "msg_x",
                emailDate = "2026-01-01",
                detectedType = null,
                detectedAmountPaisa = null,
                emailSubject = null,
                emailSender = null,
                linkedFundEntryId = null,
                scannedAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertNull(restored.detectedType)
        assertNull(restored.detectedAmountPaisa)
        assertNull(restored.emailSubject)
        assertNull(restored.emailSender)
        assertNull(restored.linkedFundEntryId)
    }

    // -----------------------------------------------------------------------
    // GmailFilterEntity
    // -----------------------------------------------------------------------

    @Test
    fun `GmailFilterEntity round-trip preserves all fields`() {
        val entity =
            GmailFilterEntity(
                id = 1L,
                filterType = "SENDER",
                filterValue = "noreply@zerodha.com",
                isActive = 1,
                createdAt = epochMs,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.filterType, restored.filterType)
        assertEquals(entity.filterValue, restored.filterValue)
        assertEquals(entity.isActive, restored.isActive)
        assertEquals(entity.createdAt, restored.createdAt)
    }

    @Test
    fun `GmailFilterEntity isActive=0 survives round-trip`() {
        val entity =
            GmailFilterEntity(
                id = 2L,
                filterType = "SENDER",
                filterValue = "x@x.com",
                isActive = 0,
                createdAt = epochMs,
            )
        assertEquals(0, entity.toProto().toEntity().isActive)
    }

    // -----------------------------------------------------------------------
    // PersistentAlertEntity
    // -----------------------------------------------------------------------

    @Test
    fun `PersistentAlertEntity round-trip preserves all fields`() {
        val entity =
            PersistentAlertEntity(
                id = 1L,
                alertType = "HOLDINGS_MISMATCH",
                severity = "CRITICAL",
                payload = """{"symbol":"INFY"}""",
                acknowledged = 0,
                createdAt = epochMs,
                resolvedAt = null,
                resolvedBy = null,
            )
        val restored = entity.toProto().toEntity()
        assertEquals(entity.id, restored.id)
        assertEquals(entity.alertType, restored.alertType)
        assertEquals(entity.severity, restored.severity)
        assertEquals(entity.payload, restored.payload)
        assertEquals(entity.acknowledged, restored.acknowledged)
        assertEquals(entity.createdAt, restored.createdAt)
        assertNull(restored.resolvedAt)
        assertNull(restored.resolvedBy)
    }

    @Test
    fun `PersistentAlertEntity resolved fields survive round-trip`() {
        val entity =
            PersistentAlertEntity(
                id = 2L,
                alertType = "SYNC_FAILED",
                severity = "WARNING",
                payload = "{}",
                acknowledged = 1,
                createdAt = epochMs,
                resolvedAt = epochMs,
                resolvedBy = "USER_ACK",
            )
        val restored = entity.toProto().toEntity()
        assertEquals(1, restored.acknowledged)
        assertEquals(entity.resolvedAt, restored.resolvedAt)
        assertEquals(entity.resolvedBy, restored.resolvedBy)
    }

    // -----------------------------------------------------------------------
    // UserPreferences
    // -----------------------------------------------------------------------

    @Test
    fun `UserPreferences round-trip preserves all fields`() {
        val prefs =
            UserPreferences(
                themeMode = "DARK",
                orderSyncTimes = listOf("09:00", "15:30"),
                reconciliationTime = "20:00",
                chargeRateRefreshDays = 30,
                reconciliationTolerancePaisa = 100L,
                gmailEnabled = true,
                scheduledBackupEnabled = true,
                backupIntervalDays = 7,
                backupDestination = "GOOGLE_DRIVE",
                lockTimeoutMinutes = 5,
                defaultProfitTargetType = "PERCENTAGE",
                defaultProfitTargetValue = 500,
            )
        val restored = prefs.toProto().toDomain()
        assertEquals(prefs.themeMode, restored.themeMode)
        assertEquals(prefs.orderSyncTimes, restored.orderSyncTimes)
        assertEquals(prefs.reconciliationTime, restored.reconciliationTime)
        assertEquals(prefs.chargeRateRefreshDays, restored.chargeRateRefreshDays)
        assertEquals(prefs.reconciliationTolerancePaisa, restored.reconciliationTolerancePaisa)
        assertEquals(prefs.gmailEnabled, restored.gmailEnabled)
        assertEquals(prefs.scheduledBackupEnabled, restored.scheduledBackupEnabled)
        assertEquals(prefs.backupIntervalDays, restored.backupIntervalDays)
        assertEquals(prefs.backupDestination, restored.backupDestination)
        assertEquals(prefs.lockTimeoutMinutes, restored.lockTimeoutMinutes)
        assertEquals(prefs.defaultProfitTargetType, restored.defaultProfitTargetType)
        assertEquals(prefs.defaultProfitTargetValue, restored.defaultProfitTargetValue)
    }
}
