package com.kitewatch.ui.component

enum class SyncStatus {
    /** Last sync succeeded and was within the last 24 hours. */
    SYNCED,

    /** No sync has occurred yet, or last sync succeeded but was more than 24 hours ago. */
    STALE,

    /** Last sync failed. */
    FAILED,
}
