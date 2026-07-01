package com.spendwise.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TransactionIdSynthesizerTest {

    @Test
    fun `identical inputs produce the same hex-encoded SHA-256 id`() {
        val date = Instant.parse("2026-06-28T10:15:42Z")
        val first = TransactionIdSynthesizer.synthesize("user-1", "swiggy@okicici", -100.0, date)
        val second = TransactionIdSynthesizer.synthesize("user-1", "swiggy@okicici", -100.0, date)

        assertEquals(first, second)
        assertEquals(64, first.length) // 32-byte SHA-256 digest, hex-encoded
        assertTrue(first.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `truncates transaction date to the minute per the synthesis rule`() {
        val date1 = Instant.parse("2026-06-28T10:15:00Z")
        val date2 = Instant.parse("2026-06-28T10:15:59Z")

        val id1 = TransactionIdSynthesizer.synthesize("user-1", "swiggy@okicici", -100.0, date1)
        val id2 = TransactionIdSynthesizer.synthesize("user-1", "swiggy@okicici", -100.0, date2)

        assertEquals(id1, id2)
    }

    @Test
    fun `different amount produces a different id`() {
        val date = Instant.parse("2026-06-28T10:15:00Z")
        val id1 = TransactionIdSynthesizer.synthesize("user-1", "swiggy@okicici", -100.0, date)
        val id2 = TransactionIdSynthesizer.synthesize("user-1", "swiggy@okicici", -200.0, date)

        assertNotEquals(id1, id2)
    }

    @Test
    fun `different user produces a different id`() {
        val date = Instant.parse("2026-06-28T10:15:00Z")
        val id1 = TransactionIdSynthesizer.synthesize("user-1", "swiggy@okicici", -100.0, date)
        val id2 = TransactionIdSynthesizer.synthesize("user-2", "swiggy@okicici", -100.0, date)

        assertNotEquals(id1, id2)
    }
}
