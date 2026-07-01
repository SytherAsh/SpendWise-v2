package com.spendwise.sync

import com.spendwise.storage.QueuedTransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeIngestApiClient(
    private val resultsByCall: List<List<IngestItemResult>> = emptyList(),
    private val throwOnCall: Int? = null,
) : IngestApiClient {
    var callCount = 0
        private set

    override suspend fun postBatch(
        transactions: List<QueuedTransactionEntity>,
        userJwt: String,
        deviceApiKey: String,
    ): List<IngestItemResult> {
        val callIndex = callCount
        callCount++
        if (throwOnCall == callIndex) throw IOException("simulated network failure")
        return resultsByCall.getOrElse(callIndex) { emptyList() }
    }
}

class BatchSyncEngineTest {

    private fun entity(id: String) = QueuedTransactionEntity(
        transactionId = id,
        transactionDate = "2026-06-28T10:15:00Z",
        debit = 100.0,
        credit = 0.0,
        amount = -100.0,
        drCrIndicator = "DR",
        recipientName = null,
        upiId = null,
        bank = "SBIN",
        transactionMode = "UPI",
        note = null,
        source = "sms",
    )

    private val session: () -> Pair<String, String>? = { "jwt-token" to "device-key" }

    @Test
    fun `mixed batch response - 201, 409, 500 - leaves only the 500 item pending`() = runTest {
        val dao = FakeQueuedTransactionDao()
        dao.seed(entity("txn-201"), entity("txn-409"), entity("txn-500"))

        val apiClient = FakeIngestApiClient(
            resultsByCall = listOf(
                listOf(
                    IngestItemResult("txn-201", 201),
                    IngestItemResult("txn-409", 409),
                    IngestItemResult("txn-500", 500),
                ),
            ),
        )

        val engine = BatchSyncEngine(dao, apiClient, session)
        val outcome = engine.syncOnce()

        assertEquals(3, outcome.attempted)
        assertEquals(2, outcome.succeeded)
        assertEquals(1, outcome.stillPending)

        val stillUnsynced = dao.getUnsynced()
        assertEquals(1, stillUnsynced.size)
        assertEquals("txn-500", stillUnsynced.first().transactionId)
    }

    @Test
    fun `a 409 on one item does not fail the rest of the batch`() = runTest {
        val dao = FakeQueuedTransactionDao()
        dao.seed(entity("txn-a"), entity("txn-b"))

        val apiClient = FakeIngestApiClient(
            resultsByCall = listOf(
                listOf(IngestItemResult("txn-a", 409), IngestItemResult("txn-b", 201)),
            ),
        )

        val engine = BatchSyncEngine(dao, apiClient, session)
        val outcome = engine.syncOnce()

        assertEquals(2, outcome.succeeded)
        assertTrue(dao.getUnsynced().isEmpty())
    }

    @Test
    fun `network failure leaves everything queued for the next scheduled run`() = runTest {
        val dao = FakeQueuedTransactionDao()
        dao.seed(entity("txn-1"), entity("txn-2"))

        val apiClient = FakeIngestApiClient(throwOnCall = 0)

        val engine = BatchSyncEngine(dao, apiClient, session)
        val outcome = engine.syncOnce()

        assertEquals(0, outcome.succeeded)
        assertEquals(2, outcome.stillPending)
        assertEquals(2, dao.getUnsynced().size)
    }

    @Test
    fun `items that failed on the first attempt are retried and succeed on the next run`() = runTest {
        val dao = FakeQueuedTransactionDao()
        dao.seed(entity("txn-1"))

        val apiClient = FakeIngestApiClient(
            resultsByCall = listOf(
                listOf(IngestItemResult("txn-1", 500)),
                listOf(IngestItemResult("txn-1", 201)),
            ),
        )

        val engine = BatchSyncEngine(dao, apiClient, session)
        engine.syncOnce()
        assertEquals(1, dao.getUnsynced().size)

        val secondOutcome = engine.syncOnce()
        assertEquals(1, secondOutcome.succeeded)
        assertTrue(dao.getUnsynced().isEmpty())
    }
}
