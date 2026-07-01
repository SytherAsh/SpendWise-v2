package com.spendwise.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DedupCheckerTest {

    private fun sampleTransaction(transactionId: String?) = ParsedTransaction(
        transactionDate = Instant.parse("2026-06-28T10:15:00Z"),
        debit = 100.0,
        credit = 0.0,
        amount = -100.0,
        drCrIndicator = "DR",
        transactionId = transactionId,
        recipientName = "Swiggy",
        upiId = "swiggy@okicici",
        bank = "ICICI",
        transactionMode = "UPI",
    )

    @Test
    fun `same bank-provided transaction id twice returns null on the second call`() {
        val transaction = sampleTransaction("bank-ref-001")
        val existingBefore = emptySet<String>()

        val firstResult = DedupChecker.check(transaction, existingBefore)
        assertEquals(transaction, firstResult)

        val existingAfter = setOf("bank-ref-001")
        val secondResult = DedupChecker.check(transaction, existingAfter)
        assertNull(secondResult)
    }

    @Test
    fun `same synthesized transaction id twice returns null on the second call`() {
        val synthesized = TransactionIdSynthesizer.synthesize(
            userId = "user-1",
            upiIdOrRecipientName = "swiggy@okicici",
            amount = -100.0,
            transactionDate = Instant.parse("2026-06-28T10:15:00Z"),
        )
        val transaction = sampleTransaction(synthesized)

        val firstResult = DedupChecker.check(transaction, emptySet())
        assertEquals(transaction, firstResult)

        val secondResult = DedupChecker.check(transaction, setOf(synthesized))
        assertNull(secondResult)
    }

    @Test
    fun `a genuinely different transaction returns the parsed object`() {
        val existing = setOf("bank-ref-001")
        val different = sampleTransaction("bank-ref-002")

        val result = DedupChecker.check(different, existing)
        assertEquals(different, result)
    }
}
