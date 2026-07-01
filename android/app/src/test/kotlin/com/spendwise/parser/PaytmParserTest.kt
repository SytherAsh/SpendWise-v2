package com.spendwise.parser

import com.spendwise.parser.samples.SampleSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PaytmParserTest {

    private val userId = "test-user-1"
    private val receivedAt: Instant = Instant.parse("2026-06-28T10:15:00Z")

    @Test
    fun `debit sample extracts all required fields, DR-consistent`() {
        val result = PaytmParser.parse(SampleSms.PAYTM_DEBIT, userId, receivedAt)

        assertNotNull(result.transactionDate)
        assertNotNull(result.amount)
        assertNotNull(result.debit)
        assertNotNull(result.credit)
        assertNotNull(result.drCrIndicator)
        assertNotNull(result.transactionId)

        assertEquals("DR", result.drCrIndicator)
        assertTrue(result.amount!! < 0)
        assertTrue(result.debit!! > 0)
        assertEquals(0.0, result.credit!!, 0.0)
        assertEquals(200.0, result.debit!!, 0.0)
        assertEquals(-200.0, result.amount!!, 0.0)
        assertEquals("Swiggy", result.recipientName)
        assertEquals("PAYTM123456", result.transactionId)
        assertEquals("PYTM", result.bank)
    }

    @Test
    fun `partial-data variant without recipient or ref does not throw and synthesizes an id`() {
        val result = PaytmParser.parse(SampleSms.PAYTM_PARTIAL, userId, receivedAt)

        assertNotNull(result)
        assertEquals("DR", result.drCrIndicator)
        assertEquals(150.0, result.debit!!, 0.0)
        assertEquals(null, result.recipientName)
        assertNotNull(result.transactionId)
    }
}
