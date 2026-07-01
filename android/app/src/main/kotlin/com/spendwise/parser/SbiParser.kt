package com.spendwise.parser

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses SBI debit/credit SMS formats (docs/testing.md §3 "SBI SMS formats").
 *
 * SBI messages frequently carry no time-of-day, only a date (e.g. "on 28-Jun-2026") — the
 * device's SMS-received timestamp (`receivedAt`) supplies the time-of-day component. When a
 * message doesn't match either known SBI shape at all, falls back to a best-effort loose
 * extraction rather than throwing (docs/testing.md "must not crash the parser").
 */
object SbiParser {

    private val dateFormat = DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH)

    private val debitRegex = Regex(
        """(?i)a/c\s*\S*\s*is\s+debited\s+for\s+rs\.?\s*([\d,]+(?:\.\d+)?)\s*on\s*(\d{1,2}-[A-Za-z]{3}-\d{4})(?:.*?ref\s*no\.?\s*[:.]?\s*(\S+))?"""
    )

    private val creditRegex = Regex(
        """(?i)(?:inr|rs\.?)\s*([\d,]+(?:\.\d+)?)\s*credited\s+to\s+(?:your\s+)?a/c\s*\S*\s*on\s*(\d{1,2}-[A-Za-z]{3}-\d{4})(?:.*?ref\s*no\.?\s*[:.]?\s*(\S+))?"""
    )

    private val looseAmountRegex = Regex("""(?i)rs\.?\s*([\d,]+(?:\.\d+)?)""")

    fun parse(smsText: String, userId: String, receivedAt: Instant): ParsedTransaction {
        debitRegex.find(smsText)?.let { return buildResult(it, drCr = "DR", userId = userId, receivedAt = receivedAt) }
        creditRegex.find(smsText)?.let { return buildResult(it, drCr = "CR", userId = userId, receivedAt = receivedAt) }
        return looseFallback(smsText)
    }

    private fun buildResult(match: MatchResult, drCr: String, userId: String, receivedAt: Instant): ParsedTransaction {
        val amount = parseAmount(match.groupValues[1])
        val parsedDate = runCatching { LocalDate.parse(match.groupValues[2], dateFormat) }.getOrNull()
        val transactionDate = parsedDate
            ?.atTime(receivedAt.atZone(ZoneOffset.UTC).toLocalTime())
            ?.toInstant(ZoneOffset.UTC)
            ?: receivedAt
        val bankRef = match.groupValues[3].takeIf { it.isNotBlank() }
        val signedAmount = if (drCr == "DR") -amount else amount
        val transactionId = bankRef ?: TransactionIdSynthesizer.synthesize(
            userId = userId,
            upiIdOrRecipientName = null,
            amount = signedAmount,
            transactionDate = transactionDate,
        )
        return ParsedTransaction(
            transactionDate = transactionDate,
            debit = if (drCr == "DR") amount else 0.0,
            credit = if (drCr == "CR") amount else 0.0,
            amount = signedAmount,
            drCrIndicator = drCr,
            transactionId = transactionId,
            recipientName = null,
            upiId = null,
            bank = "SBIN",
            transactionMode = if (bankRef != null) "UPI" else null,
            source = "sms",
        )
    }

    private fun looseFallback(smsText: String): ParsedTransaction {
        val amount = looseAmountRegex.find(smsText)?.groupValues?.get(1)?.let(::parseAmount)
        val drCr = when {
            amount == null -> null
            Regex("(?i)debit").containsMatchIn(smsText) -> "DR"
            Regex("(?i)credit").containsMatchIn(smsText) -> "CR"
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
            upiId = null,
            bank = "SBIN",
            transactionMode = if (smsText.contains("UPI", ignoreCase = true)) "UPI" else null,
            source = "sms",
        )
    }

    private fun parseAmount(raw: String): Double = raw.replace(",", "").toDouble()
}
