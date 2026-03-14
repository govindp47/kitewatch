package com.kitewatch.feature.gtt.mapper

import com.kitewatch.domain.model.GttRecord
import com.kitewatch.domain.model.GttStatus
import com.kitewatch.feature.gtt.model.GttUiModel
import com.kitewatch.ui.formatter.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val syncFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)

internal fun GttRecord.toUiModel(): GttUiModel =
    GttUiModel(
        gttId = gttId,
        stockCode = stockCode,
        triggerPrice = CurrencyFormatter.format(triggerPrice),
        quantity = quantity.toString(),
        statusLabel = status.toLabel(),
        isManualOverride = status == GttStatus.MANUAL_OVERRIDE_DETECTED,
        lastSynced = lastSyncedAt?.formatSyncTime() ?: "Never",
    )

private fun GttStatus.toLabel(): String =
    when (this) {
        GttStatus.PENDING_CREATION -> "Pending creation"
        GttStatus.ACTIVE -> "Active"
        GttStatus.TRIGGERED -> "Triggered"
        GttStatus.MANUAL_OVERRIDE_DETECTED -> "Manual override"
        GttStatus.PENDING_UPDATE -> "Pending update"
        GttStatus.ARCHIVED -> "Archived"
    }

private fun Instant.formatSyncTime(): String = syncFormatter.format(atZone(ZoneId.systemDefault()))
