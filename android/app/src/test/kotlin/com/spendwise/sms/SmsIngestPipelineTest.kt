package com.spendwise.sms

import com.spendwise.parser.samples.SampleSms
import com.spendwise.storage.UserSessionProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SmsIngestPipelineTest {

    private val session = object : UserSessionProvider {
        override fun getUserId(): String = "test-user-1"
    }
    private val receivedAt: Instant = Instant.parse("2026-06-28T10:15:00Z")

    @Test
    fun `financial SMS results in a new Room DB row`() = runTest {
        val dao = FakeQueuedTransactionDao()
        val pipeline = SmsIngestPipeline(dao, session)

        val enqueued = pipeline.process("VM-SBIINB", SampleSms.SBI_DEBIT, receivedAt)

        assertTrue(enqueued)
        assertEquals(1, dao.insertedRows.size)
        assertEquals(500.0, dao.insertedRows.first().debit, 0.0)
    }

    @Test
    fun `non-financial SMS results in no row`() = runTest {
        val dao = FakeQueuedTransactionDao()
        val pipeline = SmsIngestPipeline(dao, session)

        val enqueued = pipeline.process("VM-HDFCBK", SampleSms.OTP, receivedAt)

        assertFalse(enqueued)
        assertTrue(dao.insertedRows.isEmpty())
    }

    @Test
    fun `a duplicate financial SMS is not enqueued twice`() = runTest {
        val dao = FakeQueuedTransactionDao()
        val pipeline = SmsIngestPipeline(dao, session)

        val first = pipeline.process("VM-SBIINB", SampleSms.SBI_DEBIT, receivedAt)
        val second = pipeline.process("VM-SBIINB", SampleSms.SBI_DEBIT, receivedAt)

        assertTrue(first)
        assertFalse(second)
        assertEquals(1, dao.insertedRows.size)
    }
}
