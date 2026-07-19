package com.spendwise.admin.dto;

import com.spendwise.common.schedule.JobSchedule;

import java.time.Instant;

public record JobScheduleResponse(
        String jobKey,
        String displayName,
        String scheduleType,
        Integer intervalValue,
        String intervalUnit,
        String dayOfWeek,
        Integer hourOfDay,
        Instant updatedAt) {

    public static JobScheduleResponse from(JobSchedule schedule) {
        return new JobScheduleResponse(
                schedule.jobKey(),
                schedule.displayName(),
                schedule.scheduleType(),
                schedule.intervalValue(),
                schedule.intervalUnit(),
                schedule.dayOfWeek(),
                schedule.hourOfDay(),
                schedule.updatedAt());
    }
}
