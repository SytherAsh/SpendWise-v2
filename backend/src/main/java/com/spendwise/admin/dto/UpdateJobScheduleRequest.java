package com.spendwise.admin.dto;

import com.spendwise.common.schedule.InvalidJobScheduleException;
import com.spendwise.common.schedule.JobSchedule;

import java.util.Set;

/**
 * Which fields are required depends on {@code scheduleType} (interval vs. weekly), so this can't
 * be expressed with bean-validation annotations alone — {@link #validate()} does the cross-field
 * check explicitly and is called from {@code AdminServiceImpl} before either repository write.
 */
public record UpdateJobScheduleRequest(
        String scheduleType, Integer intervalValue, String intervalUnit, String dayOfWeek, Integer hourOfDay) {

    private static final Set<String> VALID_INTERVAL_UNITS = Set.of("MINUTES", "HOURS", "DAYS");
    private static final Set<String> VALID_DAYS_OF_WEEK = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    public void validate() {
        if (JobSchedule.TYPE_INTERVAL.equals(scheduleType)) {
            if (intervalValue == null || intervalValue < 1) {
                throw new InvalidJobScheduleException("intervalValue must be at least 1");
            }
            if (intervalUnit == null || !VALID_INTERVAL_UNITS.contains(intervalUnit)) {
                throw new InvalidJobScheduleException("intervalUnit must be one of " + VALID_INTERVAL_UNITS);
            }
        } else if (JobSchedule.TYPE_WEEKLY.equals(scheduleType)) {
            if (dayOfWeek == null || !VALID_DAYS_OF_WEEK.contains(dayOfWeek)) {
                throw new InvalidJobScheduleException("dayOfWeek must be one of " + VALID_DAYS_OF_WEEK);
            }
            if (hourOfDay == null || hourOfDay < 0 || hourOfDay > 23) {
                throw new InvalidJobScheduleException("hourOfDay must be between 0 and 23");
            }
        } else {
            throw new InvalidJobScheduleException(
                    "scheduleType must be '" + JobSchedule.TYPE_INTERVAL + "' or '" + JobSchedule.TYPE_WEEKLY + "'");
        }
    }
}
