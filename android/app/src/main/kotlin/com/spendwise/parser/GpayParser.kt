package com.spendwise.parser

import java.time.Instant

/**
 * Parses Google Pay UPI SMS formats (docs/testing.md §3 "GPay SMS format"). Like Paytm, GPay
 * messages carry no date/time in the body — `receivedAt` supplies `transactionDate`.
 */
object GpayParser {

    private val sentRegex = Regex(
        """(?i)you have sent\s+rs\.?\s*([\d,]+(?:\.\d+)?)\s+to\s+([\w.\-]+@[\w.\-]+)\s+using\s+google\s+pay\s+upi"""
    )

    private val receivedRegex = Regex(
        """(?i)you have received\s+rs\.?\s*([\d,]+(?:\.\d+)?)\s+from\s+([\w.\-]+@[\w.\-]+)\s+using\s+google\s+pay\s+upi"""
    )

    private val looseAmountRegex = Regex("""(?i)rs\.?\s*([\d,]+(?:\.\d+)?)""")
    private val looseUpiIdRegex = Regex("""[\w.\-]+@[\w.\-]+""")

    fun parse(smsText: String, userId: String, receivedAt: Instant): ParsedTransaction {
        sentRegex.find(smsText)?.let { return buildResult(it, drCr = "DR", userId = userId, receivedAt = receivedAt) }
        receivedRegex.find(smsText)?.let { return buildResult(it, drCr = "CR", userId = userId, receivedAt = receivedAt) }

        // Doesn't match a known GPay shape: best-effort, never throw.
        val amount = looseAmountRegex.find(smsText)?.groupValues?.get(1)?.let(::parseAmount)
        val upiId = looseUpiIdRegex.find(smsText)?.value
        val drCr = when {
            amount == null -> null
            Regex("(?i)sent").containsMatchIn(smsText) -> "DR"
            Regex("(?i)received").containsMatchIn(smsText) -> "CR"
            else -> null
        }
        return ParsedTransaction(
            transactionDate = null,
            debit = if (drCr == "DR") amount else if (drCr != null) 0.0 else null,
            credit = if (drCr == "CR") amount else if (drCr != null) 0.0 else null,
            amount = if (drCr == "DR") amount?.let { -it } else amount,
            drCrIndicator = drCr,
            transactionId = null,
            recipientName = null,
            upiId = upiId,
            bank = null,
            transactionMode = if (smsText.contains("UPI", ignoreCase = true)) "UPI" else null,
            source = "sms",
        )
    }

    private fun buildResult(match: MatchResult, drCr: String, userId: String, receivedAt: Instant): ParsedTransaction {
        val amount = parseAmount(match.groupValues[1])
        val upiId = match.groupValues[2]
        val signedAmount = if (drCr == "DR") -amount else amount
        val transactionId = TransactionIdSynthesizer.synthesize(
            userId = userId,
            upiIdOrRecipientName = upiId,
            amount = signedAmount,
            transactionDate = receivedAt,
        )
        return ParsedTransaction(
            transactionDate = receivedAt,
            debit = if (drCr == "DR") amount else 0.0,
            credit = if (drCr == "CR") amount else 0.0,
            amount = signedAmount,
            drCrIndicator = drCr,
            transactionId = transactionId,
            recipientName = null,
            upiId = upiId,
            bank = null,
            transactionMode = "UPI",
            source = "sms",
        )
    }

    private fun parseAmount(raw: String): Double = raw.replace(",", "").toDouble()
}
