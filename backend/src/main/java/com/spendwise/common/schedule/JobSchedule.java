package com.spendwise.common.schedule;

import java.time.Instant;

/**
 * One `job_schedules` row (V16, ADR-018). {@code scheduleType} determines which of the two
 * mutually-exclusive field groups is populated — enforced by the table's own check constraint,
 * not re-validated here (this is a read model; writes go through {@link JobScheduleRepository}'s
 * two separate {@code updateInterval}/{@code updateWeekly} methods, each of which can only
 * produce a well-formed row).
 */
public record JobSchedule(
        String jobKey,
        String displayName,
        String scheduleType,
        Integer intervalValue,
        String intervalUnit,
        String dayOfWeek,
        Integer hourOfDay,
        Instant updatedAt) {

    public static final String TYPE_INTERVAL = "INTERVAL";
    public static final String TYPE_WEEKLY = "WEEKLY";

    /**
     * Every schedule — interval or weekly — is expressed as a six-field Spring cron expression
     * so {@link JobScheduleTrigger} has exactly one code path (`CronExpression.parse(...).next(...)`)
     * regardless of which kind of row it's reading. The "asterisk, slash, N" step syntax used for
     * interval schedules below is standard cron, not a SpendWise-specific extension.
     */
    public String toCronExpression() {
        if (TYPE_WEEKLY.equals(scheduleType)) {
            return "0 0 " + hourOfDay + " * * " + dayOfWeek;
        }
        return switch (intervalUnit) {
            case "MINUTES" -> "0 */" + intervalValue + " * * * *";
            case "HOURS" -> "0 0 */" + intervalValue + " * * *";
            case "DAYS" -> "0 0 0 */" + intervalValue + " * *";
            default -> throw new IllegalStateException("Unknown interval_unit '" + intervalUnit + "' for job '" + jobKey + "'");
        };
    }
}
