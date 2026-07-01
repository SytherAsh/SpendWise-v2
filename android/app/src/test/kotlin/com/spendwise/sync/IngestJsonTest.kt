package com.spendwise.sync

import com.spendwise.storage.QueuedTransactionEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the encoded batch matches the `POST /ingest/transactions` request schema in
 * docs/api.md byte-for-byte in shape (the epic's "Working Milestone"). Robolectric is required
 * here — plain JVM unit tests hit Android's stubbed `org.json.JSONObject` ("not mocked").
 */
@RunWith(RobolectricTestRunner::class)
class IngestJsonTest {

    @Test
    fun `encoded batch matches the ingest request schema shape`() {
        val entity = QueuedTransactionEntity(
            transactionId = "txn_abc123",
            transactionDate = "2025-06-15T14:32:00Z",
            debit = 350.0,
            credit = 0.0,
            amount = -350.0,
            drCrIndicator = "DR",
            recipientName = "Swiggy",
            upiId = "swiggy@okicici",
            bank = "ICICI",
            transactionMode = "UPI",
            note = null,
            source = "sms",
        )

        val json = JSONObject(IngestJson.encodeBatch(listOf(entity)))
        assertTrue(json.has("transactions"))

        val txn = json.getJSONArray("transactions").getJSONObject(0)
        assertEquals("2025-06-15T14:32:00Z", txn.getString("transaction_date"))
        assertEquals(350.0, txn.getDouble("debit"), 0.0)
        assertEquals(0.0, txn.getDouble("credit"), 0.0)
        assertEquals(-350.0, txn.getDouble("amount"), 0.0)
        assertEquals("DR", txn.getString("dr_cr_indicator"))
        assertEquals("txn_abc123", txn.getString("transaction_id"))
        assertEquals("Swiggy", txn.getString("recipient_name"))
        assertEquals("swiggy@okicici", txn.getString("upi_id"))
        assertEquals("ICICI", txn.getString("bank"))
        assertEquals("UPI", txn.getString("transaction_mode"))
        assertTrue(txn.isNull("note"))
        assertEquals("sms", txn.getString("source"))
    }

    @Test
    fun `decodeResults falls back to the whole-response status when the body doesn't match the expected shape`() {
        val results = IngestJson.decodeResults(
            body = "",
            fallbackStatus = 500,
            transactionIds = listOf("txn-1", "txn-2"),
        )

        assertEquals(2, results.size)
        assertTrue(results.all { it.httpStatus == 500 })
    }
}
