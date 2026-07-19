package com.spendwise.common.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-018 — every schedule shape must resolve to a valid, correctly-behaving cron expression,
 * since {@link JobScheduleTrigger} treats that as the single source of truth regardless of
 * whether the row is INTERVAL or WEEKLY. */
class JobScheduleTest {

    @Test
    void intervalMinutesProducesAStepCronExpression() {
        JobSchedule schedule =
                new JobSchedule("categorization_retry", "Categorization retry", "INTERVAL", 30, "MINUTES", null, null, Instant.now());

        assertThat(schedule.toCronExpression()).isEqualTo("0 */30 * * * *");
        assertThat(CronExpression.isValidExpression(schedule.toCronExpression())).isTrue();
    }

    @Test
    void intervalHoursProducesAStepCronExpression() {
        JobSchedule schedule = new JobSchedule(
                "recommendation_generation", "Recommendation generator", "INTERVAL", 6, "HOURS", null, null, Instant.now());

        assertThat(schedule.toCronExpression()).isEqualTo("0 0 */6 * * *");
    }

    @Test
    void intervalDaysProducesAStepCronExpression() {
        JobSchedule schedule = new JobSchedule("some_job", "Some Job", "INTERVAL", 2, "DAYS", null, null, Instant.now());

        assertThat(schedule.toCronExpression()).isEqualTo("0 0 0 */2 * *");
    }

    @Test
    void weeklyProducesADayAndHourCronExpression() {
        JobSchedule schedule =
                new JobSchedule("canonicalization", "Recipient canonicalization sweep", "WEEKLY", null, null, "SUN", 4, Instant.now());

        assertThat(schedule.toCronExpression()).isEqualTo("0 0 4 * * SUN");
        assertThat(CronExpression.isValidExpression(schedule.toCronExpression())).isTrue();
    }

    @Test
    void unknownIntervalUnitThrows() {
        JobSchedule schedule = new JobSchedule("bad_job", "Bad Job", "INTERVAL", 5, "FORTNIGHTS", null, null, Instant.now());

        assertThatThrownBy(schedule::toCronExpression).isInstanceOf(IllegalStateException.class);
    }
}
