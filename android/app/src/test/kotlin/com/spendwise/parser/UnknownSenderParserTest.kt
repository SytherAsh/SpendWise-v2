package com.spendwise.parser

import com.spendwise.parser.samples.SampleSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class UnknownSenderParserTest {

    private val userId = "test-user-1"
    private val receivedAt: Instant = Instant.parse("2026-07-01T09:00:00Z")

    @Test
    fun `financial-looking SMS from a fictitious sender does not throw, recovers amount and dr_cr when present`() {
        val result = UnknownSenderParser.parse(SampleSms.UNKNOWN_SENDER_FINANCIAL, userId, receivedAt)

        assertNotNull(result)
        assertEquals(799.0, result.debit!!, 0.0)
        assertEquals("DR", result.drCrIndicator)
        assertEquals(-799.0, result.amount!!, 0.0)
    }

    @Test
    fun `SMS with no recoverable amount does not throw and leaves fields null`() {
        val result = UnknownSenderParser.parse("Your service request has been registered.", userId, receivedAt)

        assertNotNull(result)
        assertEquals(null, result.amount)
        assertEquals(null, result.drCrIndicator)
        assertEquals(null, result.transactionId)
    }
}
