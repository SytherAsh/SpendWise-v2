package com.spendwise.common.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Writes {@code admin_logs} rows (docs/spec/database.md) so a background job's swallowed failure
 * is visible in the admin portal's Logs view ({@code GET /admin/logs}, {@link
 * com.spendwise.admin.AdminRepository#findLogs}) instead of only a console {@code log.warn} that
 * only whoever is watching that terminal ever sees. Uses the same {@code spendwise_jobs}
 * (BYPASSRLS) pool {@code AdminRepository} itself reads from — a job's failure is rarely scoped
 * to any one RLS session, and {@code admin_logs.user_id} is nullable for exactly that system-wide
 * case (see {@link JobsDataSourceConfig}'s sanctioned-callers list, extended here to cover this
 * writer alongside the {@code @Scheduled} job classes it's called from).
 *
 * <p>Lives in {@code common.db} rather than the Admin module — Admin only ever <em>reads</em>
 * {@code admin_logs}; making other modules depend on the Admin module to write it would risk the
 * exact circular dependency CLAUDE.md's "no circular dependencies between modules" invariant
 * forbids, since Admin itself already depends on Categorization/Transaction/etc.
 *
 * <p>Deliberately never throws — a logging failure must never turn an already-handled-and-swallowed
 * caller failure into an unhandled one.
 */
@Component
public class AdminEventLog {

    private static final Logger log = LoggerFactory.getLogger(AdminEventLog.class);

    private final JdbcTemplate jobsJdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminEventLog(@Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate, ObjectMapper objectMapper) {
        this.jobsJdbcTemplate = jobsJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** {@code userId} is null for a system-wide event not scoped to one user (e.g. a cross-user lookup failing). */
    public void record(String eventType, UUID userId, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            jobsJdbcTemplate.update(
                    "INSERT INTO admin_logs (event_type, user_id, payload) VALUES (?, ?, ?::jsonb)", eventType, userId, json);
        } catch (Exception e) {
            log.warn("Failed to write admin_logs event '{}': {}", eventType, e.getMessage());
        }
    }
}
