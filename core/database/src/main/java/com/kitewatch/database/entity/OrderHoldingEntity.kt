package com.kitewatch.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table linking orders to holdings for FIFO lot tracking.
 * Composite primary key (order_id, holding_id).
 * Both foreign keys cascade delete so orphan links are never left behind.
 */
@Entity(
    tableName = "order_holdings",
    primaryKeys = ["order_id", "holding_id"],
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = HoldingEntity::class,
            parentColumns = ["id"],
            childColumns = ["holding_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["holding_id"]),
    ],
)
data class OrderHoldingEntity(
    @ColumnInfo(name = "order_id")
    val orderId: Long,
    @ColumnInfo(name = "holding_id")
    val holdingId: Long,
    /** Quantity from this order contributing to this holding */
    val quantity: Int,
)
