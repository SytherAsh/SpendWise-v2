package com.spendwise.common.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TriggerContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** ADR-018 — {@link JobScheduleTrigger} must re-read the schedule on every call (not cache it at
 * construction), and use the last completion time (not "now") as its base when one exists, so a
 * job that runs late doesn't get its next run computed from the wrong anchor. */
class JobScheduleTriggerTest {

    private final JobScheduleRepository repository = mock(JobScheduleRepository.class);

    @Test
    void computesNextExecutionFromLastCompletionForAnIntervalSchedule() {
        JobSchedule schedule =
                new JobSchedule("categorization_retry", "Categorization retry", "INTERVAL", 30, "MINUTES", null, null, Instant.now());
        given(repository.findByJobKey("categorization_retry")).willReturn(Optional.of(schedule));
        JobScheduleTrigger trigger = new JobScheduleTrigger("categorization_retry", repository);

        // A completion at exactly a half-hour boundary -- the next :00/:30-aligned firing is
        // exactly 30 minutes later, not "now + 30 minutes" from whenever the test runs.
        Instant lastCompletion = Instant.parse("2026-07-19T10:30:00Z");
        TriggerContext context = mock(TriggerContext.class);
        given(context.lastCompletion()).willReturn(lastCompletion);

        Instant next = trigger.nextExecution(context);

        assertThat(next).isEqualTo(Instant.parse("2026-07-19T11:00:00Z"));
    }

    @Test
    void fallsBackToNowWhenThereIsNoPriorCompletion() {
        JobSchedule schedule =
                new JobSchedule("categorization_retry", "Categorization retry", "INTERVAL", 30, "MINUTES", null, null, Instant.now());
        given(repository.findByJobKey("categorization_retry")).willReturn(Optional.of(schedule));
        JobScheduleTrigger trigger = new JobScheduleTrigger("categorization_retry", repository);

        TriggerContext context = mock(TriggerContext.class);
        given(context.lastCompletion()).willReturn(null);

        Instant next = trigger.nextExecution(context);

        assertThat(next).isAfter(Instant.now());
        assertThat(next).isBeforeOrEqualTo(Instant.now().plus(30, ChronoUnit.MINUTES).plusSeconds(60));
    }

    @Test
    void reReadsTheScheduleOnEveryCallRatherThanCachingIt() {
        JobSchedule thirtyMinutes =
                new JobSchedule("categorization_retry", "Categorization retry", "INTERVAL", 30, "MINUTES", null, null, Instant.now());
        JobSchedule oneHour =
                new JobSchedule("categorization_retry", "Categorization retry", "INTERVAL", 1, "HOURS", null, null, Instant.now());
        given(repository.findByJobKey("categorization_retry")).willReturn(Optional.of(thirtyMinutes)).willReturn(Optional.of(oneHour));
        JobScheduleTrigger trigger = new JobScheduleTrigger("categorization_retry", repository);
        TriggerContext context = mock(TriggerContext.class);
        given(context.lastCompletion()).willReturn(Instant.parse("2026-07-19T10:00:00Z"));

        Instant first = trigger.nextExecution(context);
        Instant second = trigger.nextExecution(context);

        assertThat(first).isEqualTo(Instant.parse("2026-07-19T10:30:00Z"));
        assertThat(second).isEqualTo(Instant.parse("2026-07-19T11:00:00Z"));
    }

    @Test
    void computesTheNextMatchingSundayForAWeeklySchedule() {
        JobSchedule schedule =
                new JobSchedule("canonicalization", "Recipient canonicalization sweep", "WEEKLY", null, null, "SUN", 4, Instant.now());
        given(repository.findByJobKey("canonicalization")).willReturn(Optional.of(schedule));
        JobScheduleTrigger trigger = new JobScheduleTrigger("canonicalization", repository);

        // 2026-07-19 is itself a Sunday; completing at 04:00 that day means the next matching
        // fire is the FOLLOWING Sunday, not later the same day.
        TriggerContext context = mock(TriggerContext.class);
        given(context.lastCompletion()).willReturn(Instant.parse("2026-07-19T04:00:00Z"));

        Instant next = trigger.nextExecution(context);

        assertThat(next).isEqualTo(Instant.parse("2026-07-26T04:00:00Z"));
    }

    @Test
    void missingScheduleRowThrowsRatherThanSilentlyStoppingTheJob() {
        given(repository.findByJobKey("unknown_job")).willReturn(Optional.empty());
        JobScheduleTrigger trigger = new JobScheduleTrigger("unknown_job", repository);
        TriggerContext context = mock(TriggerContext.class);

        assertThatThrownBy(() -> trigger.nextExecution(context)).isInstanceOf(JobScheduleNotFoundException.class);
    }
}
