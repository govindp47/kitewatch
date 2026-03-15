package com.kitewatch.infra.backup

import com.kitewatch.infra.backup.proto.BackupHeader
import com.kitewatch.infra.backup.proto.ChargeRate
import com.kitewatch.infra.backup.proto.FundEntry
import com.kitewatch.infra.backup.proto.GmailFilter
import com.kitewatch.infra.backup.proto.GmailScanCache
import com.kitewatch.infra.backup.proto.GttRecord
import com.kitewatch.infra.backup.proto.Holding
import com.kitewatch.infra.backup.proto.KiteWatchBackup
import com.kitewatch.infra.backup.proto.Order
import com.kitewatch.infra.backup.proto.OrderHoldingsLink
import com.kitewatch.infra.backup.proto.PersistentAlert
import com.kitewatch.infra.backup.proto.PnlMonthlyCache
import com.kitewatch.infra.backup.proto.Transaction
import com.kitewatch.infra.backup.proto.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KiteWatchBackupProtoRoundTripTest {
    @Test
    fun `round-trip serialization preserves all fields across all 13 message types`() {
        val original =
            KiteWatchBackup
                .newBuilder()
                .setHeader(
                    BackupHeader
                        .newBuilder()
                        .setFormatVersion(1)
                        .setSchemaVersion(3)
                        .setCreatedAt("2026-03-15T10:00:00Z")
                        .setAccountId("ZQ1234")
                        .setAppVersion("1.0.0")
                        .setRecordCount(42L)
                        .build(),
                ).addOrders(
                    Order
                        .newBuilder()
                        .setId(1L)
                        .setZerodhaOrderId("ORD001")
                        .setStockCode("INFY")
                        .setStockName("Infosys Ltd")
                        .setExchange("NSE")
                        .setOrderType("BUY")
                        .setQuantity(10)
                        .setPricePaisa(150000L)
                        .setTotalValuePaisa(1500000L)
                        .setTradeDate("2026-03-15")
                        .setTradeTimestamp("2026-03-15T09:30:00Z")
                        .setSettlementId("2026315")
                        .setInstrumentToken(408065)
                        .setSource("SYNC")
                        .setCreatedAt("2026-03-15T09:30:00Z")
                        .build(),
                ).addHoldings(
                    Holding
                        .newBuilder()
                        .setId(1L)
                        .setStockCode("INFY")
                        .setStockName("Infosys Ltd")
                        .setExchange("NSE")
                        .setQuantity(10)
                        .setInvestedAmountPaisa(1500000L)
                        .setAvgBuyPricePaisa(150000L)
                        .setTotalBuyChargesPaisa(3000L)
                        .setProfitTargetType("PERCENTAGE")
                        .setProfitTargetValue(500)
                        .setTargetSellPricePaisa(157500L)
                        .setInstrumentToken(408065)
                        .setCreatedAt("2026-03-15T09:30:00Z")
                        .setUpdatedAt("2026-03-15T09:30:00Z")
                        .build(),
                ).addTransactions(
                    Transaction
                        .newBuilder()
                        .setId(1L)
                        .setType("DEBIT")
                        .setSubType("TRADE")
                        .setReferenceId("ORD001")
                        .setReferenceType("ORDER")
                        .setStockCode("INFY")
                        .setAmountPaisa(1500000L)
                        .setRunningFundBalancePaisa(8500000L)
                        .setDescription("Buy INFY 10@1500")
                        .setTransactionDate("2026-03-15")
                        .setSource("SYNC")
                        .setCreatedAt("2026-03-15T09:30:00Z")
                        .build(),
                ).addFundEntries(
                    FundEntry
                        .newBuilder()
                        .setId(1L)
                        .setEntryType("CREDIT")
                        .setAmountPaisa(10000000L)
                        .setEntryDate("2026-03-01")
                        .setNote("Initial deposit")
                        .setIsGmailDetected(false)
                        .setGmailMessageId("")
                        .setReconciliationId("")
                        .setCreatedAt("2026-03-01T00:00:00Z")
                        .build(),
                ).addChargeRates(
                    ChargeRate
                        .newBuilder()
                        .setId(1L)
                        .setRateType("STT_BUY_BPS")
                        .setRateValue(10L)
                        .setRateUnit("BPS")
                        .setEffectiveFrom("2024-01-01")
                        .setFetchedAt("2026-03-15T00:00:00Z")
                        .setIsCurrent(true)
                        .build(),
                ).addGttRecords(
                    GttRecord
                        .newBuilder()
                        .setId(1L)
                        .setZerodhaGttId(98765)
                        .setStockCode("INFY")
                        .setTriggerType("SINGLE")
                        .setTriggerPricePaisa(157500L)
                        .setSellQuantity(10)
                        .setGttStatus("ACTIVE")
                        .setIsAppManaged(true)
                        .setAppCalculatedPrice(157500L)
                        .setManualOverrideDetected(false)
                        .setHoldingId(1L)
                        .setLastSyncedAt("2026-03-15T09:30:00Z")
                        .setIsArchived(false)
                        .setCreatedAt("2026-03-15T09:30:00Z")
                        .setUpdatedAt("2026-03-15T09:30:00Z")
                        .build(),
                ).addOrderHoldingsLinks(
                    OrderHoldingsLink
                        .newBuilder()
                        .setId(1L)
                        .setOrderId(1L)
                        .setHoldingId(1L)
                        .setQuantity(10)
                        .build(),
                ).addPnlCache(
                    PnlMonthlyCache
                        .newBuilder()
                        .setId(1L)
                        .setYearMonth("2026-02")
                        .setTotalSellValuePaisa(2000000L)
                        .setTotalBuyCostSoldPaisa(1800000L)
                        .setTotalBuyChargesPaisa(5000L)
                        .setTotalSellChargesPaisa(4500L)
                        .setRealizedPnlPaisa(190500L)
                        .setInvestedValuePaisa(1800000L)
                        .setOrderCount(2)
                        .setLastUpdatedAt("2026-03-01T00:00:00Z")
                        .build(),
                ).addGmailCache(
                    GmailScanCache
                        .newBuilder()
                        .setId(1L)
                        .setGmailMessageId("msg_abc123")
                        .setDetectedType("FUND_CREDIT")
                        .setDetectedAmountPaisa(10000000L)
                        .setEmailDate("2026-03-01")
                        .setEmailSubject("Amount credited to your account")
                        .setEmailSender("noreply@zerodha.com")
                        .setStatus("CONFIRMED")
                        .setLinkedFundEntryId(1L)
                        .setScannedAt("2026-03-01T08:00:00Z")
                        .build(),
                ).addGmailFilters(
                    GmailFilter
                        .newBuilder()
                        .setId(1L)
                        .setFilterType("SENDER")
                        .setFilterValue("noreply@zerodha.com")
                        .setIsActive(true)
                        .setCreatedAt("2026-01-01T00:00:00Z")
                        .build(),
                ).addAlerts(
                    PersistentAlert
                        .newBuilder()
                        .setId(1L)
                        .setAlertType("BACKUP_STALE")
                        .setSeverity("WARNING")
                        .setPayload("{\"days_since_backup\": 35}")
                        .setAcknowledged(false)
                        .setCreatedAt("2026-03-15T00:00:00Z")
                        .setResolvedAt("")
                        .setResolvedBy("")
                        .build(),
                ).setPreferences(
                    UserPreferences
                        .newBuilder()
                        .setThemeMode("DARK")
                        .addOrderSyncTimes("09:00")
                        .addOrderSyncTimes("15:30")
                        .setReconciliationTime("20:00")
                        .setChargeRateRefreshDays(30)
                        .setReconciliationTolerancePaisa(100L)
                        .setGmailEnabled(true)
                        .setScheduledBackupEnabled(true)
                        .setBackupIntervalDays(7)
                        .setBackupDestination("GOOGLE_DRIVE")
                        .setLockTimeoutMinutes(5)
                        .setDefaultProfitTargetType("PERCENTAGE")
                        .setDefaultProfitTargetValue(500)
                        .build(),
                ).build()

        // Serialize → deserialize
        val bytes = original.toByteArray()
        val restored = KiteWatchBackup.parseFrom(bytes)

        // Verify top-level equality
        assertEquals(original, restored)

        // Spot-check individual message types to confirm correct field mapping
        val header = restored.header
        assertEquals(1, header.formatVersion)
        assertEquals(3, header.schemaVersion)
        assertEquals("2026-03-15T10:00:00Z", header.createdAt)
        assertEquals("ZQ1234", header.accountId)
        assertEquals("1.0.0", header.appVersion)
        assertEquals(42L, header.recordCount)

        assertTrue(restored.ordersList.isNotEmpty())
        assertEquals("ORD001", restored.getOrders(0).zerodhaOrderId)
        assertEquals("INFY", restored.getOrders(0).stockCode)
        assertEquals(10, restored.getOrders(0).quantity)
        assertEquals(150000L, restored.getOrders(0).pricePaisa)

        assertTrue(restored.holdingsList.isNotEmpty())
        assertEquals("INFY", restored.getHoldings(0).stockCode)
        assertEquals(500, restored.getHoldings(0).profitTargetValue)

        assertTrue(restored.transactionsList.isNotEmpty())
        assertEquals("DEBIT", restored.getTransactions(0).type)

        assertTrue(restored.fundEntriesList.isNotEmpty())
        assertEquals("CREDIT", restored.getFundEntries(0).entryType)

        assertTrue(restored.chargeRatesList.isNotEmpty())
        assertEquals("STT_BUY_BPS", restored.getChargeRates(0).rateType)
        assertEquals(true, restored.getChargeRates(0).isCurrent)

        assertTrue(restored.gttRecordsList.isNotEmpty())
        assertEquals(98765, restored.getGttRecords(0).zerodhaGttId)

        assertTrue(restored.orderHoldingsLinksList.isNotEmpty())
        assertEquals(1L, restored.getOrderHoldingsLinks(0).orderId)
        assertEquals(1L, restored.getOrderHoldingsLinks(0).holdingId)

        assertTrue(restored.pnlCacheList.isNotEmpty())
        assertEquals("2026-02", restored.getPnlCache(0).yearMonth)

        assertTrue(restored.gmailCacheList.isNotEmpty())
        assertEquals("msg_abc123", restored.getGmailCache(0).gmailMessageId)

        assertTrue(restored.gmailFiltersList.isNotEmpty())
        assertEquals("SENDER", restored.getGmailFilters(0).filterType)

        assertTrue(restored.alertsList.isNotEmpty())
        assertEquals("BACKUP_STALE", restored.getAlerts(0).alertType)

        val prefs = restored.preferences
        assertEquals("DARK", prefs.themeMode)
        assertEquals(2, prefs.orderSyncTimesCount)
        assertEquals("09:00", prefs.getOrderSyncTimes(0))
        assertEquals("15:30", prefs.getOrderSyncTimes(1))
        assertEquals(true, prefs.gmailEnabled)
        assertEquals(500, prefs.defaultProfitTargetValue)
    }

    @Test
    fun `empty KiteWatchBackup serializes and deserializes without error`() {
        val empty = KiteWatchBackup.newBuilder().build()
        val bytes = empty.toByteArray()
        val restored = KiteWatchBackup.parseFrom(bytes)
        assertEquals(empty, restored)
    }
}
