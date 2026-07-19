package com.spendwise.categorization;

import com.spendwise.common.db.AdminEventLog;
import com.spendwise.common.job.ManuallyTriggerableJob;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.UncategorizedTransactionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * E4-S3-T3 — re-triggers ML categorization for transactions ingested while FastAPI was
 * unavailable, or currently sitting on the Miscellaneous low-confidence fallback (ML strategy
 * phase, 2026-07-12 — see {@link CategorizationService#categorize}), per docs/architecture.md's
 * Background Jobs table ("Categorization retry — every 30 minutes"). Cross-user by nature —
 * {@link TransactionService#findAllUncategorized} reads via the {@code spendwise_jobs} role (see
 * {@code com.spendwise.common.db.JobsDataSourceConfig} and STATUS.md's Epic 4 close-out).
 *
 * <p>Implements {@link ManuallyTriggerableJob} both for Admin's manual "run now" trigger and as
 * the scheduling entry point {@code com.spendwise.common.schedule.DynamicJobScheduler} calls on
 * whatever cadence the admin-configurable {@code job_schedules} row for
 * {@code "categorization_retry"} currently says (ADR-018) — no more static {@code @Scheduled}
 * annotation. See {@link ManuallyTriggerableJob}'s own javadoc for why this shape rather than
 * routing through {@link CategorizationService} the way retrain/canonicalize already do.
 */
@Component
public class CategorizationRetryJob implements ManuallyTriggerableJob {

    private static final Logger log = LoggerFactory.getLogger(CategorizationRetryJob.class);

    // Not specified in docs — caps how many transactions one run re-attempts, so a large backlog
    // (e.g. after an extended FastAPI outage) can't make a single scheduled run unboundedly slow;
    // the next run 30 minutes later picks up whatever's left.
    private static final int BATCH_LIMIT = 2200;

    private final TransactionService transactionService;
    private final CategorizationService categorizationService;
    private final AdminEventLog adminEventLog;

    public CategorizationRetryJob(
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
        List<UncategorizedTransactionRef> uncategorized;
        try {
            uncategorized = transactionService.findAllUncategorized(BATCH_LIMIT, categorizationService.lowConfidenceThreshold());
        } catch (RuntimeException e) {
            // The next scheduled run retries — a transient failure here (e.g. spendwise_jobs
            // connection issue) must not crash the scheduler thread.
            log.warn("Categorization retry job's lookup failed: {}", e.getMessage());
            adminEventLog.record(
                    "categorization_retry_run", null, Map.of("status", "failure", "stage", "lookup", "error", String.valueOf(e.getMessage())));
            return;
        }

        for (UncategorizedTransactionRef ref : uncategorized) {
            try {
                // categorize() never throws per its own contract (E4-S3-T1), but isolating each
                // item here too (same pattern as IngestService/E4-S3-T2) means one bad item can
                // never stop the rest of the batch.
                categorizationService.categorize(ref.userId(), ref.transactionId());
            } catch (RuntimeException e) {
                log.warn("Categorization retry failed for transaction {}: {}", ref.transactionId(), e.getMessage());
            }
        }
        adminEventLog.record("categorization_retry_run", null, Map.of("status", "success", "retried", uncategorized.size()));
    }
}
