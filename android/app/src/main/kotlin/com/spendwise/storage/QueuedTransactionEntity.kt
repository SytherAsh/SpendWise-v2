package com.spendwise.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local offline queue row, mirroring the `POST /ingest/transactions` request payload's
 * transaction object shape (docs/api.md) plus a `synced` flag distinguishing queued-but-not-
 * yet-uploaded rows from ones the Sync module has already acknowledged.
 */
@Entity(tableName = "queued_transactions")
data class QueuedTransactionEntity(
    @PrimaryKey val transactionId: String,
    val transactionDate: String, // ISO-8601 instant, e.g. "2025-06-15T14:32:00Z"
    val debit: Double,
    val credit: Double,
    val amount: Double,
    val drCrIndicator: String,
    val recipientName: String?,
    val upiId: String?,
    val bank: String?,
    val transactionMode: String?,
    val note: String?,
    val source: String,
    val synced: Boolean = false,
)
