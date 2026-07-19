package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so that the RLS
 * session variable set by {@link RlsSession} and the query that depends on it share the same
 * connection (docs/security.md "Supabase Row-Level Security").
 *
 * <p>Every SELECT here names its columns explicitly rather than {@code SELECT *} — this is a
 * deliberate second layer of defense (beyond the response DTOs) against {@code sms_raw_text}
 * ever entering the Java domain model (E3-S1-T3).
 */
@Repository
public class TransactionRepository {

    private static final String SELECT_COLUMNS =
            "t.id, t.user_id, t.transaction_date, t.debit, t.credit, t.amount, t.balance, "
                    + "t.transaction_mode, t.dr_cr_indicator, t.transaction_id, t.recipient_name, t.bank, t.upi_id, "
                    + "t.note, t.source, t.parsed_at, t.recipient_canonical, tc.category_id, tc.confidence_score, tc.assigned_by";

    private static final RowMapper<Transaction> ROW_MAPPER = (rs, rowNum) -> new Transaction(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getTimestamp("transaction_date").toInstant(),
            rs.getBigDecimal("debit"),
            rs.getBigDecimal("credit"),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("balance"),
            rs.getString("transaction_mode"),
            rs.getString("dr_cr_indicator"),
            rs.getString("transaction_id"),
            rs.getString("recipient_name"),
            rs.getString("bank"),
            rs.getString("upi_id"),
            rs.getString("note"),
            TransactionSource.fromDbValue(rs.getString("source")),
            rs.getTimestamp("parsed_at").toInstant(),
            (Integer) rs.getObject("category_id"),
            rs.getObject("confidence_score") == null ? null : rs.getFloat("confidence_score"),
            rs.getString("assigned_by"),
            rs.getString("recipient_canonical"));

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final JdbcTemplate jobsJdbcTemplate;

    public TransactionRepository(
            JdbcTemplate jdbcTemplate, RlsSession rlsSession, @Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.jobsJdbcTemplate = jobsJdbcTemplate;
    }

    /**
     * Cross-user (E4-S3-T3) — uses the {@code spendwise_jobs} connection
     * ({@code BYPASSRLS}, see {@code com.spendwise.common.db.JobsDataSourceConfig}), never the
     * RLS-scoped {@code jdbcTemplate} above. Returns only (user_id, transaction_id) pairs, never
     * a full row — the retry job immediately re-scopes to one user at a time via {@link
     * com.spendwise.categorization.CategorizationService#categorize}, which uses the normal
     * RLS-scoped path to do the actual write.
     *
     * <p>Retry-eligible means either: (a) truly uncategorized — no {@code transaction_categories}
     * row at all (e.g. a transient FastAPI failure never persisted anything), or (b) an ML-assigned
     * fallback (Miscellaneous, {@code confidence_score < lowConfidenceThreshold}) — still eligible
     * for a better category once a later retrain improves the model (ML strategy phase,
     * 2026-07-12). {@code assigned_by = 'user'} rows (manual corrections) are never included —
     * those are never auto-overwritten.
     */
    public List<UncategorizedTransactionRef> findAllUncategorized(int limit, double lowConfidenceThreshold) {
        return jobsJdbcTemplate.query(
                "SELECT t.id, t.user_id FROM transactions t "
                        + "LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "WHERE t.deleted_at IS NULL AND (tc.transaction_id IS NULL "
                        + "OR (tc.assigned_by = 'ml' AND tc.confidence_score < ?)) "
                        + "ORDER BY t.parsed_at ASC LIMIT ?",
                (rs, rowNum) -> new UncategorizedTransactionRef(UUID.fromString(rs.getString("user_id")), UUID.fromString(rs.getString("id"))),
                lowConfidenceThreshold,
                limit);
    }

    /**
     * Secondary dedup check (docs/database.md Deduplication Strategy #2) — only meaningful when
     * {@code upiId} is non-null; callers must not invoke this for null {@code upiId} (it
     * degenerates silently, per the same doc note, so primary dedup alone governs that case).
     */
    public boolean existsBySecondaryKey(UUID userId, String upiId, BigDecimal amount, Instant transactionDate) {
        rlsSession.setCurrentUser(userId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE user_id = ? AND upi_id = ? AND amount = ? AND transaction_date = ?",
                Integer.class,
                userId,
                upiId,
                amount,
                Timestamp.from(transactionDate));
        return count != null && count > 0;
    }

    /** Relies on {@code idx_transactions_unique_dedup} — callers must catch {@link org.springframework.dao.DuplicateKeyException}. */
    public Transaction insert(UUID userId, NewTransactionData data) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        Instant parsedAt = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, balance, "
                        + "transaction_mode, dr_cr_indicator, transaction_id, recipient_name, bank, upi_id, note, source, parsed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::transaction_source, ?)",
                id,
                userId,
                Timestamp.from(data.transactionDate()),
                data.debit(),
                data.credit(),
                data.amount(),
                data.balance(),
                data.transactionMode(),
                data.drCrIndicator(),
                data.transactionId(),
                data.recipientName(),
                data.bank(),
                data.upiId(),
                data.note(),
                data.source().dbValue(),
                Timestamp.from(parsedAt));
        return new Transaction(
                id,
                userId,
                data.transactionDate(),
                data.debit(),
                data.credit(),
                data.amount(),
                data.balance(),
                data.transactionMode(),
                data.drCrIndicator(),
                data.transactionId(),
                data.recipientName(),
                data.bank(),
                data.upiId(),
                data.note(),
                data.source(),
                parsedAt,
                null,
                null,
                null,
                null); // recipient_canonical — assigned later by RecipientCanonicalizationJob
    }

    public Optional<Transaction> findById(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM transactions t LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id "
                + "WHERE t.user_id = ? AND t.id = ? AND t.deleted_at IS NULL";
        return jdbcTemplate.query(sql, ROW_MAPPER, userId, id).stream().findFirst();
    }

    public Optional<Instant> findTransactionDate(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query(
                        "SELECT transaction_date FROM transactions WHERE user_id = ? AND id = ? AND deleted_at IS NULL",
                        (rs, rowNum) -> rs.getTimestamp("transaction_date").toInstant(),
                        userId,
                        id)
                .stream()
                .findFirst();
    }

    /** @return rows affected — 0 means no matching non-deleted row for this user (already deleted, wrong owner, or doesn't exist). */
    public int softDelete(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.update(
                "UPDATE transactions SET deleted_at = now() WHERE user_id = ? AND id = ? AND deleted_at IS NULL",
                userId,
                id);
    }

    /**
     * Fetches up to {@code limitPlusOne} rows ordered newest-first by {@code (transaction_date,
     * id)} — the compound tie-break makes the seek stable even when several transactions share a
     * timestamp. {@code cursorDate}/{@code cursorId} must both be null (first page) or both
     * non-null (subsequent pages); pass {@code limit + 1} so the service layer can detect
     * {@code hasMore} without a second round trip.
     */
    public List<Transaction> listPage(
            UUID userId,
            Integer categoryId,
            boolean uncategorizedOnly,
            Instant from,
            Instant to,
            Boolean creditOnly,
            String search,
            Instant cursorDate,
            UUID cursorId,
            int limitPlusOne) {
        rlsSession.setCurrentUser(userId);
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(SELECT_COLUMNS)
                .append(" FROM transactions t LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "LEFT JOIN categories c ON c.id = tc.category_id WHERE t.user_id = ? AND t.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendCategoryAndDateFilters(sql, args, categoryId, uncategorizedOnly, from, to);
        // Independent of the category filter above (which, when active, already forces
        // debit > 0 — see appendCategoryAndDateFilters). This is the "Received" tile's filter
        // (docs/api.md "direction" — ADR-010's Transactions-page slice): every credit
        // transaction regardless of category, so it's applied with no categoryId set.
        if (creditOnly != null) {
            sql.append(creditOnly ? " AND t.credit > 0" : " AND t.debit > 0");
        }
        appendSearchFilter(sql, args, search);
        if (cursorDate != null && cursorId != null) {
            sql.append(" AND (t.transaction_date, t.id) < (?, ?::uuid)");
            args.add(Timestamp.from(cursorDate));
            args.add(cursorId.toString());
        }
        sql.append(" ORDER BY t.transaction_date DESC, t.id DESC LIMIT ?");
        args.add(limitPlusOne);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    /**
     * Top-N transactions by absolute amount, largest first — a bounded read for "biggest
     * transactions in this category" (Analytics category deep-dive), never paginated. Unlike
     * {@link #listPage}, there is no cursor: ranking by magnitude has no stable seek key across
     * concurrent inserts the way {@code (transaction_date, id)} does, so this only ever serves a
     * single top-{@code limit} page.
     */
    public List<Transaction> topByAmount(
            UUID userId, Integer categoryId, boolean uncategorizedOnly, Instant from, Instant to, int limit) {
        rlsSession.setCurrentUser(userId);
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(SELECT_COLUMNS)
                .append(" FROM transactions t LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "WHERE t.user_id = ? AND t.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        appendCategoryAndDateFilters(sql, args, categoryId, uncategorizedOnly, from, to);
        sql.append(" ORDER BY ABS(t.amount) DESC, t.transaction_date DESC, t.id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    /** Free-text filter for the Transactions page search box — matches payee, UPI id, note, or category name. */
    private static void appendSearchFilter(StringBuilder sql, List<Object> args, String search) {
        if (search == null || search.isBlank()) {
            return;
        }
        String pattern = "%" + search.trim() + "%";
        sql.append(" AND (t.recipient_name ILIKE ? OR t.upi_id ILIKE ? OR t.note ILIKE ? OR c.name ILIKE ?)");
        args.add(pattern);
        args.add(pattern);
        args.add(pattern);
        args.add(pattern);
    }

    /** Shared by {@link #listPage} and {@link #topByAmount} — the category/date WHERE fragment, identical in both. */
    private static void appendCategoryAndDateFilters(
            StringBuilder sql, List<Object> args, Integer categoryId, boolean uncategorizedOnly, Instant from, Instant to) {
        if (categoryId != null) {
            // A category filter means "show me what I spent in this category" — money received
            // (a refund, an incoming transfer) that happens to carry this category never belongs
            // in that view, so it's excluded here regardless of which category was requested.
            sql.append(" AND EXISTS (SELECT 1 FROM transaction_categories tc2 WHERE tc2.transaction_id = t.id AND tc2.category_id = ?) AND t.debit > 0");
            args.add(categoryId);
        } else if (uncategorizedOnly) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM transaction_categories tc2 WHERE tc2.transaction_id = t.id) AND t.debit > 0");
        }
        if (from != null) {
            sql.append(" AND t.transaction_date >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND t.transaction_date <= ?");
            args.add(Timestamp.from(to));
        }
    }

    /**
     * Budget module's read-only access (docs/architecture.md "Budget → Transaction (read-only)")
     * for `/budgets/progress` (E5-S1-T3) — this month's spend per category, {@code debit} only
     * (money leaving the account; {@code credit} rows are income, not spend against a budget).
     */
    public Map<Integer, BigDecimal> sumSpendByCategoryForMonth(UUID userId, int month, int year) {
        rlsSession.setCurrentUser(userId);
        List<Object[]> rows = jdbcTemplate.query(
                "SELECT tc.category_id, SUM(t.debit) AS total_spent FROM transactions t "
                        + "JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "WHERE t.user_id = ? AND EXTRACT(MONTH FROM t.transaction_date) = ? "
                        + "AND EXTRACT(YEAR FROM t.transaction_date) = ? AND t.debit > 0 AND t.deleted_at IS NULL "
                        + "GROUP BY tc.category_id",
                (rs, rowNum) -> new Object[] {rs.getInt("category_id"), rs.getBigDecimal("total_spent")},
                userId,
                month,
                year);
        Map<Integer, BigDecimal> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Integer) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    /**
     * Budget module's read-only access for `/budgets/suggestions` (E5-S1-T4) — per-category,
     * per-calendar-month spend totals over {@code [from, to)}, used to average a suggested limit.
     */
    public List<MonthlyCategorySpend> historicalMonthlySpend(UUID userId, Instant from, Instant to) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT tc.category_id, EXTRACT(MONTH FROM t.transaction_date)::int AS month, "
                        + "EXTRACT(YEAR FROM t.transaction_date)::int AS year, SUM(t.debit) AS total_spent "
                        + "FROM transactions t JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "WHERE t.user_id = ? AND t.transaction_date >= ? AND t.transaction_date < ? AND t.debit > 0 "
                        + "AND t.deleted_at IS NULL "
                        + "GROUP BY tc.category_id, month, year",
                (rs, rowNum) -> new MonthlyCategorySpend(
                        rs.getInt("category_id"), rs.getInt("month"), rs.getInt("year"), rs.getBigDecimal("total_spent")),
                userId,
                Timestamp.from(from),
                Timestamp.from(to));
    }

    /**
     * Cross-user (E5-S2-T4) — every user's per-category spend for one calendar month, in one bulk
     * read via the {@code spendwise_jobs} role, mirroring {@link #findAllUncategorized}. Backs the
     * Alerts evaluator job; never called from a per-request path.
     */
    public List<UserCategorySpend> findAllSpendForMonth(int month, int year) {
        return jobsJdbcTemplate.query(
                "SELECT t.user_id, tc.category_id, SUM(t.debit) AS total_spent FROM transactions t "
                        + "JOIN transaction_categories tc ON tc.transaction_id = t.id "
                        + "WHERE EXTRACT(MONTH FROM t.transaction_date) = ? AND EXTRACT(YEAR FROM t.transaction_date) = ? "
                        + "AND t.debit > 0 AND t.deleted_at IS NULL GROUP BY t.user_id, tc.category_id",
                (rs, rowNum) -> new UserCategorySpend(
                        UUID.fromString(rs.getString("user_id")), rs.getInt("category_id"), rs.getBigDecimal("total_spent")),
                month,
                year);
    }

    /**
     * Cross-user (E6-S2-T1) — every debit transaction since {@code since} with a non-null
     * {@code upi_id} or {@code recipient_name}, in one bulk read via the {@code spendwise_jobs}
     * role, mirroring {@link #findAllSpendForMonth}. Backs the Alerts evaluator job's
     * recurring-payment detection pass; never called from a per-request path.
     */
    public List<RecurringCandidateTransaction> findAllForRecurringDetection(Instant since) {
        return jobsJdbcTemplate.query(
                "SELECT id, user_id, transaction_date, debit, upi_id, recipient_name, recipient_canonical FROM transactions "
                        + "WHERE transaction_date >= ? AND debit > 0 AND deleted_at IS NULL "
                        + "AND (upi_id IS NOT NULL OR recipient_name IS NOT NULL)",
                (rs, rowNum) -> new RecurringCandidateTransaction(
                        UUID.fromString(rs.getString("user_id")),
                        UUID.fromString(rs.getString("id")),
                        rs.getTimestamp("transaction_date").toInstant(),
                        rs.getBigDecimal("debit"),
                        rs.getString("upi_id"),
                        rs.getString("recipient_name"),
                        rs.getString("recipient_canonical")),
                Timestamp.from(since));
    }

    /**
     * Per-user equivalent of {@link #findAllForRecurringDetection} (Merge Payees feature,
     * 2026-07-19) — same shape and same {@code since}/debit/identity filter, but scoped to one
     * already-authenticated user via the RLS-scoped {@code jdbcTemplate} rather than the {@code
     * spendwise_jobs} bypass, since this backs {@code AlertEvaluatorJob#runForUser} (an immediate,
     * per-request re-evaluation after a payee merge is confirmed) rather than the cross-user
     * scheduled sweep.
     */
    public List<RecurringCandidateTransaction> findForRecurringDetectionByUser(UUID userId, Instant since) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT id, user_id, transaction_date, debit, upi_id, recipient_name, recipient_canonical FROM transactions "
                        + "WHERE user_id = ? AND transaction_date >= ? AND debit > 0 AND deleted_at IS NULL "
                        + "AND (upi_id IS NOT NULL OR recipient_name IS NOT NULL)",
                (rs, rowNum) -> new RecurringCandidateTransaction(
                        UUID.fromString(rs.getString("user_id")),
                        UUID.fromString(rs.getString("id")),
                        rs.getTimestamp("transaction_date").toInstant(),
                        rs.getBigDecimal("debit"),
                        rs.getString("upi_id"),
                        rs.getString("recipient_name"),
                        rs.getString("recipient_canonical")),
                userId,
                Timestamp.from(since));
    }

    /**
     * Cross-user (ML strategy phase, 2026-07-13) — every distinct (user_id, recipient_name, upi_id)
     * identity across all users, in one bulk read via the {@code spendwise_jobs} role. Backs
     * {@code RecipientCanonicalizationJob}, which groups these by user and sends each user's set to
     * FastAPI {@code /normalize-recipients}. Only rows with a non-null {@code recipient_name} or
     * {@code upi_id} are returned (nothing to canonicalize otherwise). Distinct so the clustering
     * runs on the deduplicated identity set, not once per transaction.
     */
    public List<RecipientIdentity> findAllRecipientIdentities() {
        return jobsJdbcTemplate.query(
                "SELECT DISTINCT user_id, recipient_name, upi_id FROM transactions "
                        + "WHERE deleted_at IS NULL AND (recipient_name IS NOT NULL OR upi_id IS NOT NULL)",
                (rs, rowNum) -> new RecipientIdentity(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("recipient_name"),
                        rs.getString("upi_id")));
    }

    /**
     * Cross-user bulk write (ML strategy phase, 2026-07-13) — sets {@code recipient_canonical} on
     * every transaction matching one (user_id, recipient_name, upi_id) identity, via the {@code
     * spendwise_jobs} role. NULL-safe matching ({@code IS NOT DISTINCT FROM}) because both
     * recipient_name and upi_id are nullable and a plain {@code = ?} never matches NULL — the
     * identity read above deliberately preserves the NULL components, so the write must match them
     * back exactly. Never touches the raw {@code recipient_name} column.
     */
    public int updateCanonicalForIdentity(UUID userId, String recipientName, String upiId, String canonical) {
        return jobsJdbcTemplate.update(
                "UPDATE transactions SET recipient_canonical = ? "
                        + "WHERE user_id = ? AND recipient_name IS NOT DISTINCT FROM ? AND upi_id IS NOT DISTINCT FROM ? "
                        + "AND deleted_at IS NULL",
                canonical,
                userId,
                recipientName,
                upiId);
    }

    /**
     * User-triggered equivalent of {@link #updateCanonicalForIdentity} (ADR-014) — same
     * NULL-safe identity match and same scope (every transaction sharing the identity, not just
     * one row), but via the RLS-scoped {@code jdbcTemplate} rather than the {@code spendwise_jobs}
     * bypass, since this runs inside a live user request rather than the background sweep
     * (CLAUDE.md: RLS is a backstop, not a substitute for the explicit {@code user_id} guard
     * already present here).
     */
    public int updateCanonicalForIdentityAsUser(UUID userId, String recipientName, String upiId, String canonical) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.update(
                "UPDATE transactions SET recipient_canonical = ? "
                        + "WHERE user_id = ? AND recipient_name IS NOT DISTINCT FROM ? AND upi_id IS NOT DISTINCT FROM ? "
                        + "AND deleted_at IS NULL",
                canonical,
                userId,
                recipientName,
                upiId);
    }

    /**
     * Every transaction id owned by {@code userId} sharing the given identity (ADR-020) — same
     * NULL-safe match and RLS-scoped {@code jdbcTemplate} as {@link #updateCanonicalForIdentityAsUser},
     * just a read instead of a write. Backs {@code CategorizationService#recategorizeIdentity}.
     */
    public List<UUID> findTransactionIdsForIdentityAsUser(UUID userId, String recipientName, String upiId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT id FROM transactions "
                        + "WHERE user_id = ? AND recipient_name IS NOT DISTINCT FROM ? AND upi_id IS NOT DISTINCT FROM ? "
                        + "AND deleted_at IS NULL",
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                userId,
                recipientName,
                upiId);
    }
}
