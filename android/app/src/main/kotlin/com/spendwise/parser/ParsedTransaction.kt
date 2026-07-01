package com.spendwise.parser

import java.time.Instant

/**
 * On-device representation of a parsed SMS transaction, field-for-field aligned with the
 * `POST /ingest/transactions` request schema (docs/api.md). Fields are nullable because the
 * unknown-sender fallback extractor (E2-S2-T4) may not be able to recover all of them —
 * sender-specific parsers (SBI/Paytm/GPay) guarantee non-null `transactionDate`, `amount`,
 * `debit`, `credit`, `drCrIndicator`, and `transactionId` for every sample format they support.
 */
data class ParsedTransaction(
    val transactionDate: Instant?,
    val debit: Double?,
    val credit: Double?,
    val amount: Double?,
    val drCrIndicator: String?,
    val transactionId: String?,
    val recipientName: String?,
    val upiId: String?,
    val bank: String?,
    val transactionMode: String?,
    val note: String? = null,
    val source: String = "sms",
)
