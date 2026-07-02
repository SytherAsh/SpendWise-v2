package com.spendwise.transaction;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for the Transaction module's core domain (transactions themselves —
 * categories and EMIs have their own sibling interfaces). Consumed cross-module by Ingest (only
 * via {@link #persistFromIngest}) and by Categorization (only via {@link #getById}, {@link
 * #assignMlCategory}, and {@link #isCategorized}) — docs/architecture.md "Allowed module
 * dependencies".
 */
public interface TransactionService {

    /**
     * Never throws for a duplicate transaction_id — returns {@link Optional#empty()}. A present
     * result carries the persisted {@link Transaction} (including its generated {@code id}) so
     * Ingest can immediately trigger categorization (E4-S3-T2) without a second lookup.
     */
    Optional<Transaction> persistFromIngest(UUID userId, NewTransactionData data);

    /** Manual entry (E3-S2-T3) — always persists with {@code source = 'manual'} regardless of {@code data.source()}. */
    Transaction createManual(UUID userId, NewTransactionData data);

    /**
     * @param limit page size (docs/api.md default 50)
     * @param cursor last-seen transaction id from a prior page, or null for the first page
     */
    TransactionPage list(UUID userId, int limit, UUID cursor, Integer categoryId, Instant from, Instant to);

    /** @throws TransactionNotFoundException if absent or owned by a different user */
    Transaction getById(UUID userId, UUID transactionId);

    /**
     * Atomically updates {@code transaction_categories} and inserts the labeled example into
     * {@code ml_corrections} (docs/api.md "Category correction ownership" — no cross-module call
     * to Categorization). A correction that would set old = new is a no-op (E3-S2-T4 DoD).
     *
     * @throws TransactionNotFoundException if absent or owned by a different user
     * @throws InvalidCategoryException if categoryId doesn't exist
     */
    void correctCategory(UUID userId, UUID transactionId, int categoryId);

    /**
     * Writes an ML-assigned category (E4-S3-T1) — called only by the Categorization module
     * after a confident {@code /predict} response (docs/architecture.md "Categorization may
     * call: Transaction (update category)"). Never overwrites a user's own correction. No-op if
     * the transaction doesn't exist or isn't owned by {@code userId} (E4-S3-T1 DoD: the
     * Categorization module never crashes the ingest flow on a bad write).
     */
    void assignMlCategory(UUID userId, UUID transactionId, int categoryId, double confidence);

    /** Whether {@code transactionId} already has a category assignment (ML or user). */
    boolean isCategorized(UUID userId, UUID transactionId);
}
