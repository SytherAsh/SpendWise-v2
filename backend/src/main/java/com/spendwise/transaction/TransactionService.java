package com.spendwise.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for the Transaction module's core domain (transactions themselves —
 * categories and EMIs have their own sibling interfaces). Consumed cross-module by Ingest (only
 * via {@link #persistFromIngest}), by Categorization (via {@link #getById}, {@link
 * #assignMlCategory}, {@link #findAllUncategorized}, and {@link #findAllCorrections}), by Budget
 * (via {@link #sumSpendByCategoryForMonth} and {@link #historicalMonthlySpend}, its only
 * permitted read — docs/architecture.md "Budget → Transaction (read-only)"), and by Alerts (via
 * {@link #findAllSpendForMonth}) — docs/architecture.md "Allowed module dependencies".
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
     * @param categoryId filter to this category id; ignored when {@code uncategorizedOnly} is true
     * @param uncategorizedOnly filter to transactions with no category assigned at all
     * @param creditOnly {@code true} for credit-only, {@code false} for debit-only, {@code null} for no
     *     direction filter — independent of {@code categoryId}/{@code uncategorizedOnly} (docs/api.md
     *     "direction"; backs the Received view, ADR-010's Transactions-page slice)
     */
    TransactionPage list(
            UUID userId,
            int limit,
            UUID cursor,
            Integer categoryId,
            boolean uncategorizedOnly,
            Instant from,
            Instant to,
            Boolean creditOnly);

    /**
     * Top-{@code limit} transactions by absolute amount, largest first (E10 Analytics category
     * deep-dive "biggest transactions" — docs/api.md {@code GET /transactions?sort=amount_desc}).
     * A bounded, non-paginated read; see {@link TransactionRepository#topByAmount}.
     */
    List<Transaction> topByAmount(
            UUID userId, Integer categoryId, boolean uncategorizedOnly, Instant from, Instant to, int limit);

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
     * Writes an ML-assigned category (E4-S3-T1) — called only by the Categorization module, either
     * after a confident {@code /predict} response or (ML strategy phase, 2026-07-12) as the
     * Miscellaneous fallback for a low-confidence one (docs/architecture.md "Categorization may
     * call: Transaction (update category)"). Never overwrites a user's own correction. No-op if
     * the transaction doesn't exist or isn't owned by {@code userId} (E4-S3-T1 DoD: the
     * Categorization module never crashes the ingest flow on a bad write).
     */
    void assignMlCategory(UUID userId, UUID transactionId, int categoryId, double confidence);

    /**
     * Cross-user (E4-S3-T3) — every retry-eligible transaction across all users, oldest-first:
     * transactions with no {@code transaction_categories} row yet, plus (ML strategy phase,
     * 2026-07-12) ML-assigned rows still below {@code lowConfidenceThreshold} (the Miscellaneous
     * fallback), which stay eligible for an upgrade after a later retrain. Backs the
     * categorization retry job; bypasses RLS via the {@code spendwise_jobs} role (see {@code
     * com.spendwise.common.db.JobsDataSourceConfig}).
     */
    List<UncategorizedTransactionRef> findAllUncategorized(int limit, double lowConfidenceThreshold);

    /**
     * Cross-user (E4-S3-T4) — every {@code ml_corrections} row across all users, joined with its
     * transaction's feature fields. Backs the weekly ML retraining job; bypasses RLS via the
     * {@code spendwise_jobs} role.
     */
    List<MlCorrectionRecord> findAllCorrections();

    /** Budget module read-only access (E5-S1-T3) — this calendar month's spend, keyed by category_id. */
    Map<Integer, BigDecimal> sumSpendByCategoryForMonth(UUID userId, int month, int year);

    /**
     * Budget module read-only access (E5-S1-T4) — per-category monthly spend over the
     * {@code monthsBack} calendar months immediately before the current one (current month
     * excluded, since it's still in progress and would skew an average low).
     */
    List<MonthlyCategorySpend> historicalMonthlySpend(UUID userId, int monthsBack);

    /**
     * Cross-user (E5-S2-T4) — every user's per-category spend for one calendar month. Backs the
     * Alerts evaluator job; bypasses RLS via the {@code spendwise_jobs} role, same pattern as
     * {@link #findAllUncategorized}.
     */
    List<UserCategorySpend> findAllSpendForMonth(int month, int year);

    /**
     * Cross-user (E6-S2-T1) — every debit transaction dated on or after {@code since} that has a
     * non-null {@code upi_id} or {@code recipient_name} (the two possible merchant-grouping
     * keys), across all users. Backs the Alerts evaluator job's recurring-payment detection pass;
     * bypasses RLS via the {@code spendwise_jobs} role, same pattern as {@link #findAllUncategorized}.
     */
    List<RecurringCandidateTransaction> findAllForRecurringDetection(Instant since);
}
