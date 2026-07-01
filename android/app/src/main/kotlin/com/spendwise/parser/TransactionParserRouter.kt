package com.spendwise.parser

import java.time.Instant

/**
 * Routes a financial SMS to the right sender-specific parser (docs/architecture.md SMS
 * Ingestion Flow "Regex parser (SBI / Paytm / GPay rules) — unknown sender: keyword-based
 * field extraction"). Routing is content/sender-keyword based, matching real SMS sender IDs
 * being unpredictable shortcodes (e.g. "VM-SBIINB", "AX-PYTMSMS").
 */
object TransactionParserRouter {

    fun parse(smsText: String, sender: String, userId: String, receivedAt: Instant): ParsedTransaction {
        val normalizedSender = sender.uppercase()
        return when {
            normalizedSender.contains("SBI") -> SbiParser.parse(smsText, userId, receivedAt)
            smsText.contains("Paytm", ignoreCase = true) || normalizedSender.contains("PYTM") ->
                PaytmParser.parse(smsText, userId, receivedAt)
            smsText.contains("Google Pay", ignoreCase = true) || normalizedSender.contains("GPAY") ->
                GpayParser.parse(smsText, userId, receivedAt)
            else -> UnknownSenderParser.parse(smsText, userId, receivedAt)
        }
    }
}
