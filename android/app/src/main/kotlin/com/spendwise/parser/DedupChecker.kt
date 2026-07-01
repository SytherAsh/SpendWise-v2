package com.spendwise.parser

/**
 * On-device dedup check (docs/architecture.md SMS Ingestion Flow "Deduplication check").
 * `existingTransactionIds` represents whatever is already queued/stored locally — the Storage
 * module (E2-S4) supplies this from Room; this function has no Room dependency itself so it
 * stays plain-unit-testable.
 *
 * A candidate with no resolvable `transactionId` at all (the sparsest unknown-sender fallback
 * output) cannot be deduped and is passed through unchanged — there's nothing to compare.
 */
object DedupChecker {

    fun check(candidate: ParsedTransaction, existingTransactionIds: Set<String>): ParsedTransaction? {
        val id = candidate.transactionId ?: return candidate
        return if (id in existingTransactionIds) null else candidate
    }
}
