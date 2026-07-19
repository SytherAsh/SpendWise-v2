package com.spendwise.common.job;

/**
 * Lets Admin fire a {@code @Scheduled} job on demand ("run everything on a Sunday cron manually"
 * — ML strategy phase, 2026-07-19) without Admin depending on that job's concrete class, which
 * CLAUDE.md's "cross-module calls go through injected service interfaces only" invariant forbids.
 *
 * <p>Deliberately not routed through each job's owning module's existing service interface
 * (e.g. {@code AlertsService}, {@code RecommendationsService}) the way {@code
 * CategorizationService#triggerRetrain}/{@code #triggerCanonicalizationSweep} already are — for
 * {@link com.spendwise.alerts.AlertEvaluatorJob} specifically, moving its orchestration logic into
 * {@code AlertsServiceImpl} would require injecting {@code AlertDispatchService} there too, which
 * already depends on {@code AlertsService} and would create a real circular bean dependency. This
 * interface is implemented directly by the job class instead — same shape kept for {@code
 * CategorizationRetryJob}/{@code RecommendationGeneratorJob} too, for one consistent pattern
 * across all three rather than three different one-off wiring styles.
 */
public interface ManuallyTriggerableJob {

    /** Runs the job's full sweep immediately, synchronously, on the calling (HTTP request) thread. */
    void runNow();
}
