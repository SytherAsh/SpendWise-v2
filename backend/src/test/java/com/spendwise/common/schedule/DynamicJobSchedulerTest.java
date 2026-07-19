package com.spendwise.common.schedule;

import com.spendwise.common.job.ManuallyTriggerableJob;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** ADR-018 — {@link DynamicJobScheduler} registers all five jobs on startup, and {@link
 * DynamicJobScheduler#reschedule} must cancel a job's currently-scheduled future and re-register
 * it (not just leave the old one running) so an admin's schedule change actually takes effect on
 * the next computed time instead of waiting for whichever far-future run was already locked in. */
@SuppressWarnings("unchecked")
class DynamicJobSchedulerTest {

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final JobScheduleRepository jobScheduleRepository = mock(JobScheduleRepository.class);
    private final ManuallyTriggerableJob canonicalizationJob = mock(ManuallyTriggerableJob.class);
    private final ManuallyTriggerableJob mlRetrainingJob = mock(ManuallyTriggerableJob.class);
    private final ManuallyTriggerableJob categorizationRetryJob = mock(ManuallyTriggerableJob.class);
    private final ManuallyTriggerableJob alertEvaluatorJob = mock(ManuallyTriggerableJob.class);
    private final ManuallyTriggerableJob recommendationGeneratorJob = mock(ManuallyTriggerableJob.class);
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);

    private final DynamicJobScheduler scheduler = new DynamicJobScheduler(
            taskScheduler,
            jobScheduleRepository,
            canonicalizationJob,
            mlRetrainingJob,
            categorizationRetryJob,
            alertEvaluatorJob,
            recommendationGeneratorJob);

    DynamicJobSchedulerTest() {
        given(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).willReturn((ScheduledFuture) future);
    }

    @Test
    void scheduleAllRegistersAllFiveJobs() {
        scheduler.scheduleAll();

        verify(taskScheduler, times(5)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void rescheduleCancelsTheExistingFutureAndRegistersAFreshOne() {
        scheduler.scheduleAll();

        scheduler.reschedule("categorization_retry");

        verify(future).cancel(false);
        // 5 from scheduleAll() + 1 from reschedule().
        verify(taskScheduler, times(6)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void rescheduleBeforeScheduleAllStillRegistersWithoutCancellingAnything() {
        scheduler.reschedule("alert_evaluation");

        verify(future, never()).cancel(false);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void rescheduleWithAnUnknownJobKeyThrows() {
        assertThatThrownBy(() -> scheduler.reschedule("not_a_real_job")).isInstanceOf(IllegalArgumentException.class);
    }

    /** A trigger's first computation reads job_schedules via the spendwise_jobs pool; if that pool
     * is unreachable at startup (missing role in a bare test container, or a transient prod DB
     * blip), scheduleAll() must not propagate — application startup can't hard-depend on the jobs
     * pool (JobsDataSourceConfig's documented invariant). Each job is still attempted; the failing
     * ones are just skipped. */
    @Test
    void scheduleAllSwallowsAJobsPoolFailureSoStartupStillCompletes() {
        given(taskScheduler.schedule(any(Runnable.class), any(Trigger.class)))
                .willThrow(new RuntimeException("Failed to obtain JDBC Connection"));

        assertThatCode(scheduler::scheduleAll).doesNotThrowAnyException();
        verify(taskScheduler, times(5)).schedule(any(Runnable.class), any(Trigger.class));
    }
}
