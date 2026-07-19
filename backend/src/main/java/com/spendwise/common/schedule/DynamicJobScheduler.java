package com.spendwise.common.schedule;

import com.spendwise.common.job.ManuallyTriggerableJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Registers every {@link ManuallyTriggerableJob} against the admin-configurable schedule in
 * {@code job_schedules} (ADR-018, 2026-07-19) — replaces the five previously-static
 * {@code @Scheduled} annotations across the categorization/alerts/recommendations modules with
 * one shared, DB-driven mechanism. Deliberately not a declarative {@code SchedulingConfigurer}
 * (the more common way to do dynamic Spring scheduling): this class keeps a handle to each job's
 * {@link ScheduledFuture} specifically so {@link #reschedule} can cancel and re-register a single
 * job's task on demand, which a purely declarative registrar doesn't expose. Without that, an
 * admin's edit would only take effect the next time the *already-locked-in* next run fires —
 * {@link JobScheduleTrigger} re-reads the DB on every computation, but Spring doesn't re-invoke a
 * {@code Trigger} until the previously-computed instant arrives, so simply updating the row isn't
 * enough on its own for "an admin's change should apply now."
 *
 * <p>Lives in {@code common.schedule}, not any single job's own module, for the same reason
 * {@link ManuallyTriggerableJob} does — it depends on jobs across categorization, alerts, and
 * recommendations, so it can't live inside any one of them without an asymmetric dependency.
 */
@Component
public class DynamicJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicJobScheduler.class);

    // Integration tests set this false (build.gradle.kts): a bare Testcontainers Postgres has no
    // spendwise_jobs role, so each trigger's first job_schedules read retries a doomed auth for the
    // full Hikari connectionTimeout (30s) × 5 jobs per @SpringBootTest context — ~150s of dead wait
    // per context, the "~17x suite slowdown" the integrationTest task comment documents. Prod leaves
    // it true; the role exists and scheduling runs normally.
    @Value("${app.scheduling.startup-enabled:true}")
    private boolean startupSchedulingEnabled;

    private final TaskScheduler taskScheduler;
    private final JobScheduleRepository jobScheduleRepository;
    private final Map<String, ManuallyTriggerableJob> jobsByKey;
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    public DynamicJobScheduler(
            TaskScheduler taskScheduler,
            JobScheduleRepository jobScheduleRepository,
            @Qualifier("recipientCanonicalizationJob") ManuallyTriggerableJob canonicalizationJob,
            @Qualifier("mlRetrainingJob") ManuallyTriggerableJob mlRetrainingJob,
            @Qualifier("categorizationRetryJob") ManuallyTriggerableJob categorizationRetryJob,
            @Qualifier("alertEvaluatorJob") ManuallyTriggerableJob alertEvaluatorJob,
            @Qualifier("recommendationGeneratorJob") ManuallyTriggerableJob recommendationGeneratorJob) {
        this.taskScheduler = taskScheduler;
        this.jobScheduleRepository = jobScheduleRepository;
        // LinkedHashMap: registration order is startup log order, not semantically important, but
        // deterministic beats HashMap's arbitrary iteration order for zero reason not to.
        this.jobsByKey = new LinkedHashMap<>();
        this.jobsByKey.put("canonicalization", canonicalizationJob);
        this.jobsByKey.put("ml_retrain", mlRetrainingJob);
        this.jobsByKey.put("categorization_retry", categorizationRetryJob);
        this.jobsByKey.put("alert_evaluation", alertEvaluatorJob);
        this.jobsByKey.put("recommendation_generation", recommendationGeneratorJob);
    }

    /** Registers all five jobs once the context is fully up — not a bean-creation-time {@code
     * @PostConstruct}, so every other bean (and the datasource) is guaranteed ready first.
     *
     * <p>Each job is scheduled independently and a failure is logged and skipped, never propagated:
     * {@link JobScheduleTrigger}'s first computation reads {@code job_schedules} through the {@code
     * spendwise_jobs} jobs pool, and application startup must not hard-depend on that pool being
     * reachable — the invariant {@code JobsDataSourceConfig} documents (and that {@code
     * initializationFailTimeout(-1)} exists to preserve). A read failure here — a transient prod DB
     * blip, or a bare Testcontainers image without the {@code spendwise_jobs} role — leaves that one
     * job unscheduled until a restart or an admin {@link #reschedule}, the same graceful-degradation
     * posture every background job in this app already follows, rather than aborting the whole
     * context load. */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleAllOnStartup() {
        if (!startupSchedulingEnabled) {
            log.info("Startup job scheduling disabled (app.scheduling.startup-enabled=false); jobs "
                    + "remain manually triggerable and reschedulable, but are not auto-scheduled.");
            return;
        }
        scheduleAll();
    }

    /** The actual registration, split from the {@link ApplicationReadyEvent} entry point above so
     * it stays directly unit-testable and so the startup gate wraps it without duplicating it. */
    public void scheduleAll() {
        jobsByKey.keySet().forEach(jobKey -> {
            try {
                scheduleJob(jobKey);
            } catch (RuntimeException e) {
                log.warn("Could not schedule job '{}' at startup ({}); leaving it unscheduled until "
                        + "a restart or an admin reschedule", jobKey, e.getMessage());
            }
        });
    }

    private void scheduleJob(String jobKey) {
        ManuallyTriggerableJob job = jobsByKey.get(jobKey);
        ScheduledFuture<?> future = taskScheduler.schedule(job::runNow, new JobScheduleTrigger(jobKey, jobScheduleRepository));
        scheduledFutures.put(jobKey, future);
    }

    /**
     * Cancels {@code jobKey}'s currently-scheduled next run and re-registers it, so the trigger
     * re-reads {@code job_schedules} immediately instead of waiting for whichever far-future time
     * was already locked in under the old schedule. Call this right after persisting a schedule
     * change — the persistence itself doesn't do this automatically.
     *
     * @throws IllegalArgumentException if jobKey isn't one of the five registered jobs
     */
    public void reschedule(String jobKey) {
        if (!jobsByKey.containsKey(jobKey)) {
            throw new IllegalArgumentException("Unknown job key: " + jobKey);
        }
        ScheduledFuture<?> existing = scheduledFutures.get(jobKey);
        if (existing != null) {
            // false: don't interrupt a currently-running execution, just cancel the *next*
            // already-scheduled firing so scheduleJob can register a fresh one.
            existing.cancel(false);
        }
        scheduleJob(jobKey);
    }
}
