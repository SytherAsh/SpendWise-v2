package com.spendwise.transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service interface for {@code /emis}, owned by the Transaction module (docs/api.md "/emis"
 * module ownership note). Consumed cross-module by Alerts (E6) — {@link
 * #findAllActiveSourceTransactionIds()} backs recurring-payment detection's exclusion rule
 * (E6-S1), and {@link #createFromDetection} backs the confirm flow (E6-S2-T2)
 * (docs/architecture.md "Alerts | May call: Transaction (read spend; read EMIs for
 * recurring-payment detection; write — create an EMI from a confirmed recurring-payment alert)").
 */
public interface EmiService {

    /** Manual creation (E3-S3-T1) — always {@code detectedFromSms = false}, {@code sourceTransactionId = null}. */
    Emi createManual(UUID userId, String label, BigDecimal amount, Integer dueDay);

    List<Emi> listActive(UUID userId);

    /** @throws EmiNotFoundException if absent or owned by a different user */
    void update(UUID userId, UUID emiId, String label, BigDecimal amount, Integer dueDay);

    /** Sets {@code is_active = false}; never hard-deletes (E3-S3-T2 DoD). */
    void deactivate(UUID userId, UUID emiId);

    /**
     * Cross-user (E6-S1-T1) — {@code source_transaction_id} of every currently-active EMI that
     * has one. Backs the recurring-payment detector's exclusion rule; bypasses RLS via the
     * {@code spendwise_jobs} role, same pattern as {@link TransactionService#findAllUncategorized}.
     */
    Set<UUID> findAllActiveSourceTransactionIds();

    /**
     * Confirm flow (E6-S2-T2) — creates an active, {@code detectedFromSms = true} EMI linked to
     * {@code sourceTransactionId} (with {@code dueDay = null}; the user can set it afterwards via
     * {@link #update}). Idempotent: if an EMI already linked to this transaction exists, returns
     * it unchanged rather than violating {@code idx_emis_source_txn}'s uniqueness constraint —
     * confirming the same detected group twice is a no-op, not an error. {@code cadence}/{@code
     * confidenceScore} (ML strategy phase, 2026-07-11) come from the ML prediction that produced
     * the alert; both nullable.
     */
    Emi createFromDetection(UUID userId, String label, BigDecimal amount, UUID sourceTransactionId, String cadence, Double confidenceScore);

    /** Idempotency check for {@link #createFromDetection}. */
    Optional<Emi> findBySourceTransaction(UUID userId, UUID sourceTransactionId);
}
