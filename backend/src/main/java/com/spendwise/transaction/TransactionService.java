package com.spendwise.transaction;

import java.time.Instant;
import java.util.UUID;

/**
 * Service interface for the Transaction module's core domain (transactions themselves —
 * categories and EMIs have their own sibling interfaces). Consumed cross-module only by Ingest,
 * and only via {@link #persistFromIngest} (docs/architecture.md "Allowed module dependencies" —
 * Ingest may call Transaction for persistence only).
 */
public interface TransactionService {

    /** Never throws for a duplicate transaction_id — returns {@link TransactionInsertOutcome#DUPLICATE}. */
    TransactionInsertOutcome persistFromIngest(UUID userId, NewTransactionData data);

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
}
