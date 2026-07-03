package com.spendwise.analytics;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Direct, RLS-scoped SQL against {@code transactions}/{@code transaction_categories}/{@code
 * categories} — the same {@link RlsSession#setCurrentUser} pattern as {@code
 * com.spendwise.transaction.TransactionRepository}, but a wholly separate class. Analytics does
 * not call {@code TransactionService} or any other module's repository (docs/architecture.md
 * "Analytics — reads from all modules (read-only), contains no business logic"; enforced by
 * {@code AnalyticsBoundaryTest}). Every SELECT names columns explicitly — never {@code SELECT *}
 * — the same second layer of defense against {@code sms_raw_text} leaking that {@code
 * TransactionRepository} uses.
 *
 * <p>Callers must invoke these from within a {@code @Transactional} method (see {@link
 * AnalyticsServiceImpl}) so the RLS session variable and the query that depends on it share one
 * connection.
 */
@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    /** Inclusive both ends, matching {@code TransactionRepository.listPage}'s existing from/to convention. */
    public OverallTotals overallTotals(UUID userId, Instant from, Instant to) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(debit), 0) AS total_spend, COALESCE(SUM(credit), 0) AS total_income "
                        + "FROM transactions WHERE user_id = ? AND transaction_date >= ? AND transaction_date <= ?",
                (rs, rowNum) -> new OverallTotals(rs.getBigDecimal("total_spend"), rs.getBigDecimal("total_income")),
                userId,
                Timestamp.from(from),
                Timestamp.from(to));
    }

    /** Only categories with at least one transaction in range are returned. */
    public List<CategoryTotal> categoryTotals(UUID userId, Instant from, Instant to) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT tc.category_id, c.name AS category_name, COALESCE(SUM(t.debit), 0) AS total_spend, "
                        + "COALESCE(SUM(t.credit), 0) AS total_income, COUNT(*) AS txn_count "
                        + "FROM transactions t "
                        + "JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "JOIN categories c ON c.id = tc.category_id "
                        + "WHERE t.user_id = ? AND t.transaction_date >= ? AND t.transaction_date <= ? "
                        + "GROUP BY tc.category_id, c.name ORDER BY tc.category_id",
                (rs, rowNum) -> new CategoryTotal(
                        rs.getInt("category_id"),
                        rs.getString("category_name"),
                        rs.getBigDecimal("total_spend"),
                        rs.getBigDecimal("total_income"),
                        rs.getLong("txn_count")),
                userId,
                Timestamp.from(from),
                Timestamp.from(to));
    }

    /** {@code granularity} must already be validated to one of week/month/year — passed straight into {@code date_trunc}. */
    public List<TrendBucket> trends(UUID userId, String granularity, Instant from, Instant to, Integer categoryId) {
        rlsSession.setCurrentUser(userId);
        StringBuilder sql = new StringBuilder(
                "SELECT date_trunc(?, t.transaction_date) AS bucket, COALESCE(SUM(t.debit), 0) AS total_spend "
                        + "FROM transactions t LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "WHERE t.user_id = ? AND t.transaction_date >= ? AND t.transaction_date <= ?");
        List<Object> args = new ArrayList<>();
        args.add(granularity);
        args.add(userId);
        args.add(Timestamp.from(from));
        args.add(Timestamp.from(to));
        if (categoryId != null) {
            sql.append(" AND tc.category_id = ?");
            args.add(categoryId);
        }
        sql.append(" GROUP BY bucket ORDER BY bucket");
        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new TrendBucket(rs.getTimestamp("bucket").toInstant(), rs.getBigDecimal("total_spend")),
                args.toArray());
    }

    /** Unpaginated (docs/api.md "Analytics endpoints return full computed results — not paginated"), newest-first. */
    public List<AnalyticsExportRow> findAllForExport(UUID userId, Instant from, Instant to) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT t.id, t.transaction_date, t.debit, t.credit, t.amount, t.balance, t.transaction_mode, "
                        + "t.dr_cr_indicator, t.transaction_id AS bank_transaction_id, t.recipient_name, t.bank, t.upi_id, "
                        + "t.note, t.source, tc.category_id, c.name AS category_name "
                        + "FROM transactions t "
                        + "LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "LEFT JOIN categories c ON c.id = tc.category_id "
                        + "WHERE t.user_id = ? AND t.transaction_date >= ? AND t.transaction_date <= ? "
                        + "ORDER BY t.transaction_date DESC",
                (rs, rowNum) -> new AnalyticsExportRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getTimestamp("transaction_date").toInstant(),
                        rs.getBigDecimal("debit"),
                        rs.getBigDecimal("credit"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("balance"),
                        rs.getString("transaction_mode"),
                        rs.getString("dr_cr_indicator"),
                        rs.getString("bank_transaction_id"),
                        rs.getString("recipient_name"),
                        rs.getString("bank"),
                        rs.getString("upi_id"),
                        rs.getString("note"),
                        rs.getString("source"),
                        (Integer) rs.getObject("category_id"),
                        rs.getString("category_name")),
                userId,
                Timestamp.from(from),
                Timestamp.from(to));
    }
}
