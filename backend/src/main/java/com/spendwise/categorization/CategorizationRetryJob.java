package com.spendwise.categorization;

import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.UncategorizedTransactionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * E4-S3-T3 — re-triggers ML categorization for transactions ingested while FastAPI was
 * unavailable (or that scored below the confidence threshold), per docs/architecture.md's
 * Background Jobs table ("Categorization retry — every 30 minutes"). Cross-user by nature —
 * {@link TransactionService#findAllUncategorized} reads via the {@code spendwise_jobs} role (see
 * {@code com.spendwise.common.db.JobsDataSourceConfig} and STATUS.md's Epic 4 close-out).
 */
@Component
public class CategorizationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(CategorizationRetryJob.class);

    // Not specified in docs — caps how many transactions one run re-attempts, so a large backlog
    // (e.g. after an extended FastAPI outage) can't make a single scheduled run unboundedly slow;
    // the next run 30 minutes later picks up whatever's left.
    private static final int BATCH_LIMIT = 500;

    private final TransactionService transactionService;
    private final CategorizationService categorizationService;

    public CategorizationRetryJob(TransactionService transactionService, CategorizationService categorizationService) {
        this.transactionService = transactionService;
        this.categorizationService = categorizationService;
    }

    // initialDelay so this doesn't fire the instant the app starts (Spring's default for
    // fixedRate with no initialDelay) -- a full system-wide categorization sweep on every app
    // restart/redeploy is wasteful and not what "every 30 minutes" means. Also avoids hitting
    // the jobs DataSource before the app has had a moment to settle.
    @Scheduled(initialDelay = 30, fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void retryUncategorized() {
        run();
    }

    /** Package-visible so tests can invoke it directly rather than waiting on the real schedule. */
    void run() {
        List<UncategorizedTransactionRef> uncategorized;
        try {
            uncategorized = transactionService.findAllUncategorized(BATCH_LIMIT);
        } catch (RuntimeException e) {
            // The next scheduled run retries — a transient failure here (e.g. spendwise_jobs
            // connection issue) must not crash the scheduler thread.
            log.warn("Categorization retry job's lookup failed: {}", e.getMessage());
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
    }
}
