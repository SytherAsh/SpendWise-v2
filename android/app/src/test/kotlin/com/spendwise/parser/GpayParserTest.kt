package com.spendwise.parser

import com.spendwise.parser.samples.SampleSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GpayParserTest {

    private val userId = "test-user-1"
    private val receivedAt: Instant = Instant.parse("2026-06-28T10:15:00Z")

    @Test
    fun `debit sample extracts all required fields, DR-consistent`() {
        val result = GpayParser.parse(SampleSms.GPAY_DEBIT, userId, receivedAt)

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
        assertEquals(350.0, result.debit!!, 0.0)
        assertEquals(-350.0, result.amount!!, 0.0)
        assertEquals("restaurant@okhdfc", result.upiId)
        assertEquals(null, result.bank)
    }

    @Test
    fun `partial-data variant without amount or upi id does not throw`() {
        val result = GpayParser.parse(SampleSms.GPAY_PARTIAL, userId, receivedAt)
        assertNotNull(result)
        assertEquals(null, result.amount)
        assertEquals(null, result.transactionId)
    }
}
