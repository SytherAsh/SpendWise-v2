package com.spendwise.parser

import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Synthesizes a stable `transaction_id` for SMS that carry no bank-provided reference number,
 * per the rule in docs/database.md (`transactions.transaction_id` column comment):
 * `hex(SHA-256(user_id || upi_id_or_recipient_name || amount || date_trunc('minute', transaction_date)))`.
 *
 * The exact serialization of each component only needs to be internally deterministic — the
 * backend does not recompute this hash, it only enforces uniqueness of whatever string value
 * is transmitted (docs/database.md "Deduplication Strategy").
 */
object TransactionIdSynthesizer {

    fun synthesize(
        userId: String,
        upiIdOrRecipientName: String?,
        amount: Double,
        transactionDate: Instant,
    ): String {
        val truncated = transactionDate.truncatedTo(ChronoUnit.MINUTES)
        val raw = buildString {
            append(userId)
            append(upiIdOrRecipientName.orEmpty())
            append(amount)
            append(truncated)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
