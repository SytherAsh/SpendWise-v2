package com.spendwise.categorization;

import com.spendwise.common.db.AdminEventLog;
import com.spendwise.common.job.ManuallyTriggerableJob;
import com.spendwise.transaction.TransactionService;
import org.springframework.stereotype.Component;

/**
 * Weekly recipient-name canonicalization (ML strategy phase, 2026-07-13) — populates each
 * transaction's {@code recipient_canonical} column via the FastAPI {@code /normalize-recipients}
 * endpoint, so recurring-payment detection groups spelling variants of one payee together and the
 * UI can show a clean name (docs/spec/decisions.md ADR for canonicalization).
 *
 * <p>Unlike categorization's {@code /predict} (one call per transaction at ingest), canonicalization
 * is inherently a whole-history batch operation: the clustering algorithm compares every recipient
 * name in a user's history against every other, so a name can only be assigned a canonical form
 * relative to the full set. Hence a scheduled sweep rather than an ingest-time hook, on the same
 * weekly cadence as {@link MlRetrainingJob} (the established batch-ML precedent).
 *
 * <p>The actual sweep body lives in {@link RecipientCanonicalizationSweep}, shared with Admin's
 * manual trigger ({@link CategorizationServiceImpl#triggerCanonicalizationSweep}) so both entry
 * points run identical logic rather than two copies that can drift apart.
 *
 * <p>Scheduling itself is no longer a static {@code @Scheduled} annotation — {@code
 * com.spendwise.common.schedule.DynamicJobScheduler} calls {@link #runNow} on whatever cadence
 * the admin-configurable {@code job_schedules} row for {@code "canonicalization"} currently says
 * (ADR-018), so an admin can change "every Sunday" to something else without a redeploy.
 */
@Component
public class RecipientCanonicalizationJob implements ManuallyTriggerableJob {

    private final TransactionService transactionService;
    private final CategorizationService categorizationService;
    private final AdminEventLog adminEventLog;

    public RecipientCanonicalizationJob(
            TransactionService transactionService, CategorizationService categorizationService, AdminEventLog adminEventLog) {
        this.transactionService = transactionService;
        this.categorizationService = categorizationService;
        this.adminEventLog = adminEventLog;
    }

    @Override
    public void runNow() {
        run();
    }

    /** Package-visible so tests can invoke it directly rather than waiting on the real schedule. */
    void run() {
        RecipientCanonicalizationSweep.run(transactionService, categorizationService, adminEventLog);
    }
}
