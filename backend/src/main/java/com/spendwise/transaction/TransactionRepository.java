package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
                    + "t.note, t.source, t.parsed_at, tc.category_id, tc.confidence_score, tc.assigned_by";

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
            rs.getString("assigned_by"));

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public TransactionRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
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
                null);
    }

    public Optional<Transaction> findById(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM transactions t LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id "
                + "WHERE t.user_id = ? AND t.id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, userId, id).stream().findFirst();
    }

    public Optional<Instant> findTransactionDate(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query(
                        "SELECT transaction_date FROM transactions WHERE user_id = ? AND id = ?",
                        (rs, rowNum) -> rs.getTimestamp("transaction_date").toInstant(),
                        userId,
                        id)
                .stream()
                .findFirst();
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
            Instant from,
            Instant to,
            Instant cursorDate,
            UUID cursorId,
            int limitPlusOne) {
        rlsSession.setCurrentUser(userId);
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(SELECT_COLUMNS)
                .append(" FROM transactions t LEFT JOIN transaction_categories tc ON tc.transaction_id = t.id WHERE t.user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (categoryId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM transaction_categories tc2 WHERE tc2.transaction_id = t.id AND tc2.category_id = ?)");
            args.add(categoryId);
        }
        if (from != null) {
            sql.append(" AND t.transaction_date >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND t.transaction_date <= ?");
            args.add(Timestamp.from(to));
        }
        if (cursorDate != null && cursorId != null) {
            sql.append(" AND (t.transaction_date, t.id) < (?, ?::uuid)");
            args.add(Timestamp.from(cursorDate));
            args.add(cursorId.toString());
        }
        sql.append(" ORDER BY t.transaction_date DESC, t.id DESC LIMIT ?");
        args.add(limitPlusOne);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }
}
