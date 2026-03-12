package com.kitewatch.domain.model

/**
 * Computed charge breakdown for a single equity delivery order.
 * All fields are in [Paisa]. Use [total] to get the aggregate charge amount.
 */
data class ChargeBreakdown(
    val stt: Paisa,
    val exchangeTxn: Paisa,
    val sebiCharges: Paisa,
    val stampDuty: Paisa,
    val gst: Paisa,
) {
    fun total(): Paisa = stt + exchangeTxn + sebiCharges + stampDuty + gst
}
