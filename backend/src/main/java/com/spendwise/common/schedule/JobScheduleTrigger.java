package com.spendwise.common.schedule;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Computes a job's next run time from whatever its {@code job_schedules} row currently says
 * (ADR-018) — re-read on every call, not cached, so an admin's edit takes effect from the very
 * next computation. On its own this still isn't instant: once Spring has already computed and
 * locked in a concrete future firing time via the underlying {@code TaskScheduler}, this trigger
 * isn't consulted again until that time arrives. {@link DynamicJobScheduler#reschedule} is what
 * forces an immediate re-read after an admin saves a change, by cancelling and re-registering the
 * task so this trigger runs again right away.
 *
 * <p>Every schedule (interval or weekly) is expressed as a cron string ({@link
 * JobSchedule#toCronExpression}), so there's exactly one code path here regardless of which kind
 * of row is read — see that method's own note on why the interval jobs' timing is wall-clock
 * aligned (e.g. every 30 minutes means :00/:30 past the hour) rather than N-minutes-after-the-
 * previous-run, a small, deliberate semantic shift from the {@code fixedRate} annotations this
 * replaced.
 */
public class JobScheduleTrigger implements Trigger {

    private final String jobKey;
    private final JobScheduleRepository jobScheduleRepository;

    public JobScheduleTrigger(String jobKey, JobScheduleRepository jobScheduleRepository) {
        this.jobKey = jobKey;
        this.jobScheduleRepository = jobScheduleRepository;
    }

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        JobSchedule schedule =
                jobScheduleRepository.findByJobKey(jobKey).orElseThrow(() -> new JobScheduleNotFoundException(jobKey));
        CronExpression cron = CronExpression.parse(schedule.toCronExpression());

        Instant last = triggerContext.lastCompletion();
        ZonedDateTime base = (last != null ? last : Instant.now()).atZone(ZoneOffset.UTC);
        ZonedDateTime next = cron.next(base);
        return next == null ? null : next.toInstant();
    }
}
