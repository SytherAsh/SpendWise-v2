package com.spendwise.sync

import com.spendwise.storage.QueuedTransactionEntity

/** Per-item outcome of one batch POST to `/ingest/transactions` (docs/api.md). */
data class IngestItemResult(val transactionId: String, val httpStatus: Int)

/**
 * Network seam for the Sync module's batch upload (docs/architecture.md Android module map
 * "Sync: batch HTTP upload"). Mocked directly in `BatchSyncEngine` tests — see
 * docs/testing.md E2-S5-T1 "mocked HTTP layer" requirement — rather than mocking at the raw
 * socket/HttpURLConnection level, which is the standard Android testing pattern for this kind
 * of network boundary.
 */
interface IngestApiClient {
    suspend fun postBatch(
        transactions: List<QueuedTransactionEntity>,
        userJwt: String,
        deviceApiKey: String,
    ): List<IngestItemResult>
}
