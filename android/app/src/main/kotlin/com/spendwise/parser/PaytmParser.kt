package com.spendwise.parser

import java.time.Instant

/**
 * Parses Paytm UPI SMS formats (docs/testing.md §3 "Paytm SMS format"). Paytm messages carry
 * no date/time in the body — `receivedAt` (the SMS-received timestamp) is used directly as
 * `transactionDate`.
 */
object PaytmParser {

    private val paidRegex = Regex(
        """(?i)rs\.?\s*([\d,]+(?:\.\d+)?)\s*paid(?:\s+to\s+([A-Za-z0-9 &.'-]+?))?\s+using\s+paytm\s+upi\.?(?:\s*ref\s*no\.?\s*[:.]?\s*(\S+))?"""
    )

    private val looseAmountRegex = Regex("""(?i)rs\.?\s*([\d,]+(?:\.\d+)?)""")

    fun parse(smsText: String, userId: String, receivedAt: Instant): ParsedTransaction {
        paidRegex.find(smsText)?.let { match ->
            val amount = parseAmount(match.groupValues[1])
            val recipient = match.groupValues[2].trim().takeIf { it.isNotBlank() }
            val bankRef = match.groupValues[3].takeIf { it.isNotBlank() }
            val transactionId = bankRef ?: TransactionIdSynthesizer.synthesize(
                userId = userId,
                upiIdOrRecipientName = recipient,
                amount = -amount,
                transactionDate = receivedAt,
            )
            return ParsedTransaction(
                transactionDate = receivedAt,
                debit = amount,
                credit = 0.0,
                amount = -amount,
                drCrIndicator = "DR",
                transactionId = transactionId,
                recipientName = recipient,
                upiId = null,
                bank = "PYTM",
                transactionMode = "UPI",
                source = "sms",
            )
        }

        // Doesn't match the known Paytm shape: best-effort, never throw.
        val amount = looseAmountRegex.find(smsText)?.groupValues?.get(1)?.let(::parseAmount)
        return ParsedTransaction(
            transactionDate = null,
            debit = amount,
            credit = if (amount != null) 0.0 else null,
            amount = amount?.let { -it },
            drCrIndicator = if (amount != null) "DR" else null,
            transactionId = null,
            recipientName = null,
            upiId = null,
            bank = "PYTM",
            transactionMode = if (smsText.contains("UPI", ignoreCase = true)) "UPI" else null,
            source = "sms",
        )
    }

    private fun parseAmount(raw: String): Double = raw.replace(",", "").toDouble()
}
