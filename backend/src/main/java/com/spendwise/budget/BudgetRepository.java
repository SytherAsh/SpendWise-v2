package com.spendwise.budget;

import com.spendwise.common.db.RlsSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so the RLS
 * session variable and the query that depends on it share the same connection.
 */
@Repository
public class BudgetRepository {

    private static final RowMapper<Budget> ROW_MAPPER = (rs, rowNum) -> new Budget(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getInt("category_id"),
            rs.getBigDecimal("monthly_limit"),
            rs.getInt("month"),
            rs.getInt("year"));

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final JdbcTemplate jobsJdbcTemplate;

    public BudgetRepository(
            JdbcTemplate jdbcTemplate, RlsSession rlsSession, @Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.jobsJdbcTemplate = jobsJdbcTemplate;
    }

    /**
     * Idempotent upsert on {@code (user_id, category_id, month, year)} (E5-S1-T1 DoD) — repeated
     * calls with identical parameters are safe and return the same row.
     */
    public Budget upsert(UUID userId, int categoryId, BigDecimal monthlyLimit, int month, int year) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO budgets (id, user_id, category_id, monthly_limit, month, year) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (user_id, category_id, month, year) DO UPDATE SET monthly_limit = EXCLUDED.monthly_limit "
                        + "RETURNING id, user_id, category_id, monthly_limit, month, year",
                ROW_MAPPER,
                id,
                userId,
                categoryId,
                monthlyLimit,
                month,
                year);
    }

    public List<Budget> findForMonth(UUID userId, int month, int year) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT id, user_id, category_id, monthly_limit, month, year FROM budgets "
                        + "WHERE user_id = ? AND month = ? AND year = ? ORDER BY category_id",
                ROW_MAPPER,
                userId,
                month,
                year);
    }

    /**
     * Cross-user (E5-S2-T4) — every budget row for one calendar month, across all users, in one
     * bulk read via the {@code spendwise_jobs} role. Backs the Alerts evaluator job; never called
     * from a per-request path.
     */
    public List<Budget> findAllForMonth(int month, int year) {
        return jobsJdbcTemplate.query(
                "SELECT id, user_id, category_id, monthly_limit, month, year FROM budgets WHERE month = ? AND year = ?",
                ROW_MAPPER,
                month,
                year);
    }
}
