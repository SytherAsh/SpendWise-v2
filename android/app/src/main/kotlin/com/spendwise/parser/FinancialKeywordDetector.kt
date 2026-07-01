package com.spendwise.parser

/**
 * Filters incoming SMS to financial-transaction messages before any regex parsing work
 * happens. Keyword-based, not sender-based — an SMS from an unrecognized sender must still
 * be classified `true` if its content looks financial (docs/testing.md §3 "Unknown sender
 * financial SMS").
 */
object FinancialKeywordDetector {

    // Checked first: any hit here means "not financial", regardless of other content.
    private val exclusionKeywords = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "is your otp", "do not share your otp",
        "sale", "% off", "off on", "discount", "flat off",
        "win a", "you have won", "prize", "lucky draw",
        "unsubscribe", "t&c apply", "limited period offer",
    )

    private val financialKeywords = listOf(
        "debited", "credited", "credited to", "debited from", "debited for",
        "a/c", "acct", "account", "paid to", "payment of",
        "sent rs", "sent inr", "received rs", "received inr",
        "upi", "imps", "neft", "withdrawn", "balance is", "avl bal",
        "rs.", "rs ", "inr ",
    )

    fun isFinancial(smsText: String, sender: String): Boolean {
        val normalized = smsText.lowercase()

        if (exclusionKeywords.any { normalized.contains(it) }) {
            return false
        }

        return financialKeywords.any { normalized.contains(it) }
    }
}
