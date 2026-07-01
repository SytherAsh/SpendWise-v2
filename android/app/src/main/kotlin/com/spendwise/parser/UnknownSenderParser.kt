package com.spendwise.parser

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Best-effort keyword/regex extraction for a financial SMS from a sender not covered by the
 * SBI/Paytm/GPay rules (docs/architecture.md SMS Ingestion Flow "unknown sender: keyword-based
 * field extraction"). Must never throw; fields it cannot recover are left null.
 */
object UnknownSenderParser {

    private val amountRegex = Regex("""(?i)(?:rs\.?|inr)\s*([\d,]+(?:\.\d+)?)""")
    private val dateRegex = Regex("""(\d{1,2}-[A-Za-z]{3}-\d{4})""")
    private val dateFormat = DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH)
    private val recipientRegex = Regex("""(?i)(?:at|towards|to)\s+([A-Za-z][A-Za-z0-9 &.'-]{1,40})""")
    private val refRegex = Regex("""(?i)ref\s*(?:no)?\.?\s*[:.]?\s*(\S+)""")

    private val debitKeywords = listOf("debit", "paid", "sent", "spent", "withdrawn", "purchase")
    private val creditKeywords = listOf("credit", "received", "deposited", "refund")

    fun parse(smsText: String, userId: String, receivedAt: Instant): ParsedTransaction {
        val amount = runCatching {
            amountRegex.find(smsText)?.groupValues?.get(1)?.replace(",", "")?.toDouble()
        }.getOrNull()

        val normalized = smsText.lowercase()
        val drCr = when {
            amount == null -> null
            debitKeywords.any { normalized.contains(it) } -> "DR"
            creditKeywords.any { normalized.contains(it) } -> "CR"
            else -> null
        }

        val transactionDate = runCatching {
            dateRegex.find(smsText)?.groupValues?.get(1)?.let { LocalDate.parse(it, dateFormat) }
                ?.atTime(receivedAt.atZone(ZoneOffset.UTC).toLocalTime())
                ?.toInstant(ZoneOffset.UTC)
        }.getOrNull() ?: receivedAt

        val recipientName = runCatching {
            recipientRegex.find(smsText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val bankRef = runCatching {
            refRegex.find(smsText)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val signedAmount = if (drCr == "DR") amount?.let { -it } else amount
        val transactionId = bankRef ?: if (amount != null) {
            runCatching {
                TransactionIdSynthesizer.synthesize(
                    userId = userId,
                    upiIdOrRecipientName = recipientName,
                    amount = signedAmount ?: amount,
                    transactionDate = transactionDate,
                )
            }.getOrNull()
        } else {
            null
        }

        return ParsedTransaction(
            transactionDate = if (amount != null) transactionDate else null,
            debit = if (drCr == "DR") amount else if (drCr != null) 0.0 else null,
            credit = if (drCr == "CR") amount else if (drCr != null) 0.0 else null,
            amount = signedAmount,
            drCrIndicator = drCr,
            transactionId = transactionId,
            recipientName = recipientName,
            upiId = null,
            bank = null,
            transactionMode = if (smsText.contains("UPI", ignoreCase = true)) "UPI" else null,
            source = "sms",
        )
    }
}
