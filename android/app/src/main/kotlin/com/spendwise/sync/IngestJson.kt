package com.spendwise.sync

import com.spendwise.storage.QueuedTransactionEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes/decodes the `POST /ingest/transactions` wire format (docs/api.md). The response
 * shape assumed here — `{"results":[{"transactionId":..., "status":...}, ...]}` — is this
 * client's interpretation of docs/api.md's per-item 409 semantics ("A 409 on one item within
 * a batch does not fail the remaining items"); Epic 3's Ingest module implementation is the
 * source of truth and this may need to be reconciled with whatever it actually returns.
 */
object IngestJson {

    fun encodeBatch(transactions: List<QueuedTransactionEntity>): String {
        val array = JSONArray()
        transactions.forEach { txn ->
            array.put(
                JSONObject().apply {
                    put("transaction_date", txn.transactionDate)
                    put("debit", txn.debit)
                    put("credit", txn.credit)
                    put("amount", txn.amount)
                    put("dr_cr_indicator", txn.drCrIndicator)
                    put("transaction_id", txn.transactionId)
                    put("recipient_name", txn.recipientName)
                    put("upi_id", txn.upiId)
                    put("bank", txn.bank)
                    put("transaction_mode", txn.transactionMode)
                    put("note", txn.note)
                    put("source", txn.source)
                },
            )
        }
        return JSONObject().put("transactions", array).toString()
    }

    /**
     * Parses the per-item results array. If the response body can't be parsed as expected
     * (e.g. a 5xx with no body, or a whole-batch failure), every transaction in the batch
     * falls back to `fallbackStatus` so the caller's per-item retry logic still applies.
     */
    fun decodeResults(
        body: String,
        fallbackStatus: Int,
        transactionIds: List<String>,
    ): List<IngestItemResult> {
        val parsed = runCatching {
            val results = JSONObject(body).getJSONArray("results")
            (0 until results.length()).map { i ->
                val item = results.getJSONObject(i)
                IngestItemResult(
                    transactionId = item.getString("transaction_id"),
                    httpStatus = item.getInt("status"),
                )
            }
        }.getOrNull()

        if (parsed != null && parsed.size == transactionIds.size) return parsed

        return transactionIds.map { IngestItemResult(it, fallbackStatus) }
    }
}
