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
import com.kitewatch.infra.backup.proto.ChargeRate
import com.kitewatch.infra.backup.proto.FundEntry
import com.kitewatch.infra.backup.proto.GmailFilter
import com.kitewatch.infra.backup.proto.GmailScanCache
import com.kitewatch.infra.backup.proto.GttRecord
import com.kitewatch.infra.backup.proto.Holding
import com.kitewatch.infra.backup.proto.Order
import com.kitewatch.infra.backup.proto.OrderHoldingsLink
import com.kitewatch.infra.backup.proto.PersistentAlert
import com.kitewatch.infra.backup.proto.PnlMonthlyCache
import com.kitewatch.infra.backup.proto.Transaction
import java.time.Instant
import com.kitewatch.infra.backup.proto.UserPreferences as UserPreferencesProto

// ---------------------------------------------------------------------------
// Timestamp helpers — entity stores epoch millis, proto stores ISO-8601 string
// ---------------------------------------------------------------------------

private fun Long.toIsoString(): String = Instant.ofEpochMilli(this).toString()

private fun String.toEpochMillis(): Long =
    if (isBlank()) System.currentTimeMillis() else Instant.parse(this).toEpochMilli()

// ---------------------------------------------------------------------------
// OrderEntity
// ---------------------------------------------------------------------------

fun OrderEntity.toProto(): Order =
    Order
        .newBuilder()
        .setId(id)
        .setZerodhaOrderId(zerodhaOrderId)
        .setStockCode(stockCode)
        .setStockName(stockName)
        .setExchange(exchange)
        .setOrderType(orderType)
        .setQuantity(quantity)
        .setPricePaisa(pricePaisa)
        .setTotalValuePaisa(totalValuePaisa)
        .setTradeDate(tradeDate)
        // trade_date already in proto; trade_timestamp not stored in entity — use empty string
        .setTradeTimestamp("")
        .setSettlementId(settlementId ?: "")
        // instrument_token not in entity — default 0
        .setInstrumentToken(0)
        .setSource(source)
        .setCreatedAt(createdAt.toIsoString())
        .build()

fun Order.toEntity(): OrderEntity =
    OrderEntity(
        id = id,
        zerodhaOrderId = zerodhaOrderId,
        stockCode = stockCode,
        stockName = stockName,
        exchange = exchange,
        orderType = orderType,
        quantity = quantity,
        pricePaisa = pricePaisa,
        totalValuePaisa = totalValuePaisa,
        tradeDate = tradeDate,
        settlementId = settlementId.takeIf { it.isNotBlank() },
        source = source.ifBlank { "SYNC" },
        createdAt = createdAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// HoldingEntity
// ---------------------------------------------------------------------------

fun HoldingEntity.toProto(): Holding =
    Holding
        .newBuilder()
        .setId(id)
        .setStockCode(stockCode)
        .setStockName(stockName)
        // exchange not stored in entity — default empty string
        .setExchange("")
        .setQuantity(quantity)
        .setInvestedAmountPaisa(investedAmountPaisa)
        .setAvgBuyPricePaisa(avgBuyPricePaisa)
        .setTotalBuyChargesPaisa(totalBuyChargesPaisa)
        .setProfitTargetType(profitTargetType)
        .setProfitTargetValue(profitTargetValue)
        .setTargetSellPricePaisa(targetSellPricePaisa)
        // instrument_token not in entity — default 0
        .setInstrumentToken(0)
        .setCreatedAt(createdAt.toIsoString())
        .setUpdatedAt(updatedAt.toIsoString())
        .build()

fun Holding.toEntity(): HoldingEntity =
    HoldingEntity(
        id = id,
        stockCode = stockCode,
        stockName = stockName,
        quantity = quantity,
        investedAmountPaisa = investedAmountPaisa,
        avgBuyPricePaisa = avgBuyPricePaisa,
        totalBuyChargesPaisa = totalBuyChargesPaisa,
        profitTargetType = profitTargetType.ifBlank { "PERCENTAGE" },
        profitTargetValue = profitTargetValue,
        targetSellPricePaisa = targetSellPricePaisa,
        createdAt = createdAt.toEpochMillis(),
        updatedAt = updatedAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// TransactionEntity
// ---------------------------------------------------------------------------

fun TransactionEntity.toProto(): Transaction =
    Transaction
        .newBuilder()
        .setId(id)
        .setType(type)
        .setSubType(subType ?: "")
        .setReferenceId(referenceId ?: "")
        .setReferenceType(referenceType ?: "")
        .setStockCode(stockCode ?: "")
        .setAmountPaisa(amountPaisa)
        .setRunningFundBalancePaisa(runningFundBalancePaisa ?: 0L)
        .setDescription(description)
        .setTransactionDate(transactionDate)
        .setSource(source)
        .setCreatedAt(createdAt.toIsoString())
        .build()

fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        type = type,
        subType = subType.takeIf { it.isNotBlank() },
        referenceId = referenceId.takeIf { it.isNotBlank() },
        referenceType = referenceType.takeIf { it.isNotBlank() },
        stockCode = stockCode.takeIf { it.isNotBlank() },
        amountPaisa = amountPaisa,
        runningFundBalancePaisa = runningFundBalancePaisa.takeIf { it != 0L },
        description = description,
        transactionDate = transactionDate,
        source = source.ifBlank { "SYSTEM" },
        createdAt = createdAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// FundEntryEntity
// ---------------------------------------------------------------------------

fun FundEntryEntity.toProto(): FundEntry =
    FundEntry
        .newBuilder()
        .setId(id)
        .setEntryType(entryType)
        .setAmountPaisa(amountPaisa)
        .setEntryDate(entryDate)
        .setNote(note ?: "")
        // is_gmail_detected: true when entry was detected via Gmail (gmailMessageId present)
        .setIsGmailDetected(gmailMessageId != null)
        .setGmailMessageId(gmailMessageId ?: "")
        .setReconciliationId(reconciliationId ?: "")
        .setCreatedAt(createdAt.toIsoString())
        .build()

fun FundEntry.toEntity(): FundEntryEntity =
    FundEntryEntity(
        id = id,
        entryType = entryType,
        amountPaisa = amountPaisa,
        entryDate = entryDate,
        note = note.takeIf { it.isNotBlank() },
        // On restore: Gmail-detected entries that were already in backup are confirmed
        isConfirmed = 1,
        gmailMessageId = gmailMessageId.takeIf { it.isNotBlank() },
        reconciliationId = reconciliationId.takeIf { it.isNotBlank() },
        createdAt = createdAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// ChargeRateEntity
// ---------------------------------------------------------------------------

fun ChargeRateEntity.toProto(): ChargeRate =
    ChargeRate
        .newBuilder()
        .setId(id)
        .setRateType(rateType)
        .setRateValue(rateValue.toLong())
        .setRateUnit(rateUnit)
        .setEffectiveFrom(effectiveFrom)
        .setFetchedAt(fetchedAt.toIsoString())
        .setIsCurrent(isCurrent == 1)
        .build()

fun ChargeRate.toEntity(): ChargeRateEntity =
    ChargeRateEntity(
        id = id,
        rateType = rateType,
        rateValue = rateValue.toInt(),
        rateUnit = rateUnit.ifBlank { "BASIS_POINTS" },
        effectiveFrom = effectiveFrom,
        fetchedAt = fetchedAt.toEpochMillis(),
        isCurrent = if (isCurrent) 1 else 0,
    )

// ---------------------------------------------------------------------------
// GttRecordEntity
// ---------------------------------------------------------------------------

fun GttRecordEntity.toProto(): GttRecord =
    GttRecord
        .newBuilder()
        .setId(id)
        // zerodha_gtt_id is int32 in proto; entity stores Long? — null → 0
        .setZerodhaGttId(zerodhaGttId?.toInt() ?: 0)
        .setStockCode(stockCode)
        // trigger_type not stored in entity — default empty string
        .setTriggerType("")
        .setTriggerPricePaisa(triggerPricePaisa)
        // sell_quantity maps to entity.quantity
        .setSellQuantity(quantity)
        // gtt_status maps to entity.status
        .setGttStatus(status)
        .setIsAppManaged(isAppManaged == 1)
        .setAppCalculatedPrice(appCalculatedPrice ?: 0L)
        .setManualOverrideDetected(manualOverrideDetected == 1)
        .setHoldingId(holdingId ?: 0L)
        .setLastSyncedAt(lastSyncedAt?.toIsoString() ?: "")
        .setIsArchived(isArchived == 1)
        .setCreatedAt(createdAt.toIsoString())
        .setUpdatedAt(updatedAt.toIsoString())
        .build()

fun GttRecord.toEntity(): GttRecordEntity =
    GttRecordEntity(
        id = id,
        zerodhaGttId = zerodhaGttId.toLong().takeIf { it != 0L },
        stockCode = stockCode,
        triggerPricePaisa = triggerPricePaisa,
        quantity = sellQuantity,
        status = gttStatus.ifBlank { "PENDING_CREATION" },
        isAppManaged = if (isAppManaged) 1 else 0,
        appCalculatedPrice = appCalculatedPrice.takeIf { it != 0L },
        manualOverrideDetected = if (manualOverrideDetected) 1 else 0,
        holdingId = holdingId.takeIf { it != 0L },
        lastSyncedAt = lastSyncedAt.takeIf { it.isNotBlank() }?.toEpochMillis(),
        isArchived = if (isArchived) 1 else 0,
        createdAt = createdAt.toEpochMillis(),
        updatedAt = updatedAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// OrderHoldingEntity
// ---------------------------------------------------------------------------

fun OrderHoldingEntity.toProto(): OrderHoldingsLink =
    OrderHoldingsLink
        .newBuilder()
        // no id in entity — proto id defaults to 0
        .setId(0L)
        .setOrderId(orderId)
        .setHoldingId(holdingId)
        .setQuantity(quantity)
        .build()

fun OrderHoldingsLink.toEntity(): OrderHoldingEntity =
    OrderHoldingEntity(
        orderId = orderId,
        holdingId = holdingId,
        quantity = quantity,
    )

// ---------------------------------------------------------------------------
// PnlMonthlyCacheEntity
// ---------------------------------------------------------------------------

fun PnlMonthlyCacheEntity.toProto(): PnlMonthlyCache =
    PnlMonthlyCache
        .newBuilder()
        .setId(id)
        .setYearMonth(yearMonth)
        .setTotalSellValuePaisa(totalSellValuePaisa)
        .setTotalBuyCostSoldPaisa(totalBuyCostSoldPaisa)
        .setTotalBuyChargesPaisa(totalBuyChargesPaisa)
        .setTotalSellChargesPaisa(totalSellChargesPaisa)
        .setRealizedPnlPaisa(realizedPnlPaisa)
        .setInvestedValuePaisa(investedValuePaisa)
        .setOrderCount(orderCount)
        .setLastUpdatedAt(lastUpdatedAt.toIsoString())
        .build()

fun PnlMonthlyCache.toEntity(): PnlMonthlyCacheEntity =
    PnlMonthlyCacheEntity(
        id = id,
        yearMonth = yearMonth,
        totalSellValuePaisa = totalSellValuePaisa,
        totalBuyCostSoldPaisa = totalBuyCostSoldPaisa,
        totalBuyChargesPaisa = totalBuyChargesPaisa,
        totalSellChargesPaisa = totalSellChargesPaisa,
        realizedPnlPaisa = realizedPnlPaisa,
        investedValuePaisa = investedValuePaisa,
        orderCount = orderCount,
        lastUpdatedAt = lastUpdatedAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// GmailScanCacheEntity
// ---------------------------------------------------------------------------

fun GmailScanCacheEntity.toProto(): GmailScanCache =
    GmailScanCache
        .newBuilder()
        .setId(id)
        .setGmailMessageId(gmailMessageId)
        .setDetectedType(detectedType ?: "")
        .setDetectedAmountPaisa(detectedAmountPaisa ?: 0L)
        .setEmailDate(emailDate)
        .setEmailSubject(emailSubject ?: "")
        .setEmailSender(emailSender ?: "")
        .setStatus(status)
        .setLinkedFundEntryId(linkedFundEntryId ?: 0L)
        .setScannedAt(scannedAt.toIsoString())
        .build()

fun GmailScanCache.toEntity(): GmailScanCacheEntity =
    GmailScanCacheEntity(
        id = id,
        gmailMessageId = gmailMessageId,
        detectedType = detectedType.takeIf { it.isNotBlank() },
        detectedAmountPaisa = detectedAmountPaisa.takeIf { it != 0L },
        emailDate = emailDate,
        emailSubject = emailSubject.takeIf { it.isNotBlank() },
        emailSender = emailSender.takeIf { it.isNotBlank() },
        status = status.ifBlank { "PENDING_REVIEW" },
        linkedFundEntryId = linkedFundEntryId.takeIf { it != 0L },
        scannedAt = scannedAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// GmailFilterEntity
// ---------------------------------------------------------------------------

fun GmailFilterEntity.toProto(): GmailFilter =
    GmailFilter
        .newBuilder()
        .setId(id)
        .setFilterType(filterType)
        .setFilterValue(filterValue)
        .setIsActive(isActive == 1)
        .setCreatedAt(createdAt.toIsoString())
        .build()

fun GmailFilter.toEntity(): GmailFilterEntity =
    GmailFilterEntity(
        id = id,
        filterType = filterType,
        filterValue = filterValue,
        isActive = if (isActive) 1 else 0,
        createdAt = createdAt.toEpochMillis(),
    )

// ---------------------------------------------------------------------------
// PersistentAlertEntity
// ---------------------------------------------------------------------------

fun PersistentAlertEntity.toProto(): PersistentAlert =
    PersistentAlert
        .newBuilder()
        .setId(id)
        .setAlertType(alertType)
        .setSeverity(severity)
        .setPayload(payload)
        .setAcknowledged(acknowledged == 1)
        .setCreatedAt(createdAt.toIsoString())
        .setResolvedAt(resolvedAt?.toIsoString() ?: "")
        .setResolvedBy(resolvedBy ?: "")
        .build()

fun PersistentAlert.toEntity(): PersistentAlertEntity =
    PersistentAlertEntity(
        id = id,
        alertType = alertType,
        severity = severity,
        payload = payload,
        acknowledged = if (acknowledged) 1 else 0,
        createdAt = createdAt.toEpochMillis(),
        resolvedAt = resolvedAt.takeIf { it.isNotBlank() }?.toEpochMillis(),
        resolvedBy = resolvedBy.takeIf { it.isNotBlank() },
    )

// ---------------------------------------------------------------------------
// UserPreferences
// ---------------------------------------------------------------------------

fun UserPreferences.toProto(): UserPreferencesProto =
    UserPreferencesProto
        .newBuilder()
        .setThemeMode(themeMode)
        .addAllOrderSyncTimes(orderSyncTimes)
        .setReconciliationTime(reconciliationTime)
        .setChargeRateRefreshDays(chargeRateRefreshDays)
        .setReconciliationTolerancePaisa(reconciliationTolerancePaisa)
        .setGmailEnabled(gmailEnabled)
        .setScheduledBackupEnabled(scheduledBackupEnabled)
        .setBackupIntervalDays(backupIntervalDays)
        .setBackupDestination(backupDestination)
        .setLockTimeoutMinutes(lockTimeoutMinutes)
        .setDefaultProfitTargetType(defaultProfitTargetType)
        .setDefaultProfitTargetValue(defaultProfitTargetValue)
        .build()

fun UserPreferencesProto.toDomain(): UserPreferences =
    UserPreferences(
        themeMode = themeMode.ifBlank { "SYSTEM" },
        orderSyncTimes = orderSyncTimesList.toList(),
        reconciliationTime = reconciliationTime.ifBlank { "20:00" },
        chargeRateRefreshDays = chargeRateRefreshDays,
        reconciliationTolerancePaisa = reconciliationTolerancePaisa,
        gmailEnabled = gmailEnabled,
        scheduledBackupEnabled = scheduledBackupEnabled,
        backupIntervalDays = backupIntervalDays,
        backupDestination = backupDestination.ifBlank { "GOOGLE_DRIVE" },
        lockTimeoutMinutes = lockTimeoutMinutes,
        defaultProfitTargetType = defaultProfitTargetType.ifBlank { "PERCENTAGE" },
        defaultProfitTargetValue = defaultProfitTargetValue,
    )
