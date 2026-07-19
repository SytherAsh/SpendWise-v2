package com.spendwise.common.schedule;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * `job_schedules` (V16) has no RLS — system-wide config, not user data (same precedent as
 * `categories`) — but this still goes through {@code jobsJdbcTemplate} rather than the default
 * {@code jdbcTemplate}: {@link JobScheduleTrigger#nextExecution} is called from Spring's
 * TaskScheduler thread, which has no per-request RLS session (no {@code app.current_user_id} is
 * ever set there), and the admin-API reads/writes should use the same pool for one consistent
 * story about which connection this table is read through — see {@code JobsDataSourceConfig}'s
 * sanctioned-callers list.
 */
@Repository
public class JobScheduleRepository {

    private final JdbcTemplate jobsJdbcTemplate;

    public JobScheduleRepository(@Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate) {
        this.jobsJdbcTemplate = jobsJdbcTemplate;
    }

    public List<JobSchedule> findAll() {
        return jobsJdbcTemplate.query("SELECT * FROM job_schedules ORDER BY job_key", this::map);
    }

    public Optional<JobSchedule> findByJobKey(String jobKey) {
        return jobsJdbcTemplate.query("SELECT * FROM job_schedules WHERE job_key = ?", this::map, jobKey).stream().findFirst();
    }

    /** @throws JobScheduleNotFoundException if jobKey isn't a known job */
    public void updateInterval(String jobKey, int intervalValue, String intervalUnit) {
        int updated = jobsJdbcTemplate.update(
                "UPDATE job_schedules SET schedule_type = 'INTERVAL', interval_value = ?, interval_unit = ?, "
                        + "day_of_week = NULL, hour_of_day = NULL, updated_at = NOW() WHERE job_key = ?",
                intervalValue,
                intervalUnit,
                jobKey);
        if (updated == 0) {
            throw new JobScheduleNotFoundException(jobKey);
        }
    }

    /** @throws JobScheduleNotFoundException if jobKey isn't a known job */
    public void updateWeekly(String jobKey, String dayOfWeek, int hourOfDay) {
        int updated = jobsJdbcTemplate.update(
                "UPDATE job_schedules SET schedule_type = 'WEEKLY', day_of_week = ?, hour_of_day = ?, "
                        + "interval_value = NULL, interval_unit = NULL, updated_at = NOW() WHERE job_key = ?",
                dayOfWeek,
                hourOfDay,
                jobKey);
        if (updated == 0) {
            throw new JobScheduleNotFoundException(jobKey);
        }
    }

    private JobSchedule map(ResultSet rs, int rowNum) throws SQLException {
        return new JobSchedule(
                rs.getString("job_key"),
                rs.getString("display_name"),
                rs.getString("schedule_type"),
                (Integer) rs.getObject("interval_value"),
                rs.getString("interval_unit"),
                rs.getString("day_of_week"),
                (Integer) rs.getObject("hour_of_day"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
