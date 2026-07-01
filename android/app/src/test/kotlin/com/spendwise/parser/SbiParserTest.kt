package com.spendwise.parser

import com.spendwise.parser.samples.SampleSms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SbiParserTest {

    private val userId = "test-user-1"
    private val receivedAt: Instant = Instant.parse("2026-06-28T10:15:00Z")

    @Test
    fun `debit sample extracts all required fields, DR-consistent`() {
        val result = SbiParser.parse(SampleSms.SBI_DEBIT, userId, receivedAt)

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
        assertEquals(500.0, result.debit!!, 0.0)
        assertEquals(-500.0, result.amount!!, 0.0)
        assertEquals("123456789012", result.transactionId)
    }

    @Test
    fun `credit sample extracts all required fields, CR-consistent, synthesizes id when no ref present`() {
        val result = SbiParser.parse(SampleSms.SBI_CREDIT, userId, receivedAt)

        assertNotNull(result.transactionDate)
        assertNotNull(result.amount)
        assertNotNull(result.debit)
        assertNotNull(result.credit)
        assertNotNull(result.drCrIndicator)
        assertNotNull(result.transactionId)

        assertEquals("CR", result.drCrIndicator)
        assertTrue(result.amount!! > 0)
        assertTrue(result.credit!! > 0)
        assertEquals(0.0, result.debit!!, 0.0)
        assertEquals(1500.0, result.credit!!, 0.0)
        assertEquals(1500.0, result.amount!!, 0.0)
    }

    @Test
    fun `recipientName upi_id and bank are present or null, never throwing`() {
        val debit = SbiParser.parse(SampleSms.SBI_DEBIT, userId, receivedAt)
        val credit = SbiParser.parse(SampleSms.SBI_CREDIT, userId, receivedAt)

        assertEquals("SBIN", debit.bank)
        assertEquals("SBIN", credit.bank)
        // recipient_name / upi_id are absent from SBI SMS text — must be null, not throw.
        assertEquals(null, debit.recipientName)
        assertEquals(null, debit.upiId)
    }

    @Test
    fun `malformed SBI-like SMS does not throw`() {
        val result = SbiParser.parse(SampleSms.SBI_MALFORMED, userId, receivedAt)
        // No specific field is guaranteed here — only that parsing completed without an exception.
        assertNotNull(result)
    }

    @Test
    fun `same synthesized transaction id is stable across identical inputs`() {
        val first = SbiParser.parse(SampleSms.SBI_CREDIT, userId, receivedAt)
        val second = SbiParser.parse(SampleSms.SBI_CREDIT, userId, receivedAt)
        assertEquals(first.transactionId, second.transactionId)
    }
}
