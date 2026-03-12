package com.kitewatch.domain.model

sealed class SyncResult {
    data class Success(
        val newOrderCount: Int,
        val updatedGttCount: Int,
    ) : SyncResult()

    data object NoNewOrders : SyncResult()

    data class Skipped(
        val reason: String,
    ) : SyncResult()

    data class Partial(
        val succeeded: Int,
        val failed: Int,
    ) : SyncResult()
}
