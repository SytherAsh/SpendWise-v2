package com.spendwise.categorization;

import com.spendwise.common.job.ManuallyTriggerableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * E4-S3-T4 — sends accumulated {@code ml_corrections} to FastAPI {@code /retrain}, per
 * docs/architecture.md's Background Jobs table ("ML retraining — Weekly (configurable)"; ADR-003
 * adaptive supervised batch retraining). Cross-user by nature — see {@link
 * CategorizationService#triggerRetrain} and STATUS.md's Epic 4 close-out.
 *
 * <p>Scheduling itself is no longer a static {@code @Scheduled} annotation — {@code
 * com.spendwise.common.schedule.DynamicJobScheduler} calls {@link #runNow} on whatever cadence
 * the admin-configurable {@code job_schedules} row for {@code "ml_retrain"} currently says
 * (ADR-018), so "Weekly (configurable)" is now actually configurable, from the admin portal,
 * without a redeploy.
 */
@Component
public class MlRetrainingJob implements ManuallyTriggerableJob {

    private static final Logger log = LoggerFactory.getLogger(MlRetrainingJob.class);

    private final CategorizationService categorizationService;

    public MlRetrainingJob(CategorizationService categorizationService) {
        this.categorizationService = categorizationService;
    }

    @Override
    public void runNow() {
        run();
    }

    /** Package-visible so tests can invoke it directly rather than waiting on the real schedule. */
    void run() {
        try {
            categorizationService.triggerRetrain();
        } catch (RuntimeException e) {
            // Unlike the retry job, a failed retrain has no automatic catch-up — the model
            // artifact just stays as-is until the next scheduled run (or a future Admin manual
            // trigger, Epic 11). Never crash the scheduler thread over it.
            log.warn("Weekly ML retrain failed: {}", e.getMessage());
        }
    }
}
