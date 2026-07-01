package com.spendwise.sync

import com.spendwise.storage.QueuedTransactionDao
import java.io.IOException

data class SyncOutcome(val attempted: Int, val succeeded: Int, val stillPending: Int)

/**
 * Batches all unsynced rows and POSTs them, per docs/architecture.md "Deduplication is
 * two-layered" and docs/api.md `/ingest` idempotency note. Per item: `2xx` or `409` → mark
 * synced (dequeue from the unsynced view); anything else → left as unsynced, retried on the
 * next scheduled run. A whole-request network failure (no response at all) leaves every
 * queued row untouched for the same reason.
 */
class BatchSyncEngine(
    private val dao: QueuedTransactionDao,
    private val apiClient: IngestApiClient,
    private val sessionProvider: () -> Pair<String, String>?,
) {

    suspend fun syncOnce(): SyncOutcome {
        val (userJwt, deviceApiKey) = sessionProvider() ?: return SyncOutcome(0, 0, 0)

        val pending = dao.getUnsynced()
        if (pending.isEmpty()) return SyncOutcome(0, 0, 0)

        val results = try {
            apiClient.postBatch(pending, userJwt, deviceApiKey)
        } catch (e: IOException) {
            return SyncOutcome(attempted = pending.size, succeeded = 0, stillPending = pending.size)
        }

        var succeeded = 0
        for (result in results) {
            if (result.httpStatus in 200..299 || result.httpStatus == 409) {
                dao.markSynced(result.transactionId)
                succeeded++
            }
        }
        return SyncOutcome(attempted = pending.size, succeeded = succeeded, stillPending = pending.size - succeeded)
    }
}
