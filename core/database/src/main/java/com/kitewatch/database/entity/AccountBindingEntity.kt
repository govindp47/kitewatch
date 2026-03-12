package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the `account_binding` table.
 * Single-row table enforced by fixed PK = 1.
 * Stores the one permanently bound Zerodha account for this on-device DB.
 * api_key and access_token are stored in the SQLCipher-encrypted DB.
 */
@Entity(tableName = "account_binding")
data class AccountBindingEntity(
    /** Fixed PK = 1 enforces single-row invariant. Use INSERT OR REPLACE. */
    @PrimaryKey
    val id: Long = 1L,
    @ColumnInfo(name = "zerodha_user_id")
    val zerodhaUserId: String,
    @ColumnInfo(name = "user_name")
    val userName: String,
    /** Kite Connect API key — encrypted at DB level via SQLCipher */
    @ColumnInfo(name = "api_key")
    val apiKey: String,
    /** Current session token; null when expired */
    @ColumnInfo(name = "access_token")
    val accessToken: String? = null,
    /** ISO-8601 UTC string; null when no active session */
    @ColumnInfo(name = "token_expires_at")
    val tokenExpiresAt: String? = null,
    /** ISO-8601 UTC string: timestamp of initial binding */
    @ColumnInfo(name = "bound_at")
    val boundAt: String,
    /** ISO-8601 UTC string: last successful authentication */
    @ColumnInfo(name = "last_auth_at")
    val lastAuthAt: String? = null,
)
