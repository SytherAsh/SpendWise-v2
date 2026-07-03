package com.spendwise.recommendations;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so the RLS session
 * variable and the query that depends on it share the same connection (mirrors {@code
 * com.spendwise.alerts.AlertRepository}).
 */
@Repository
public class RecommendationRepository {

    private static final String SELECT_COLUMNS = "id, user_id, category_id, text, priority, generated_at, is_dismissed";

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final RowMapper<Recommendation> rowMapper = (rs, rowNum) -> mapRow(rs);

    public RecommendationRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public Recommendation insert(UUID userId, Integer categoryId, String text, RecommendationPriority priority) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO recommendations (id, user_id, category_id, text, priority) VALUES (?, ?, ?, ?, ?) "
                        + "RETURNING " + SELECT_COLUMNS,
                rowMapper,
                id,
                userId,
                categoryId,
                text,
                priority.dbValue());
    }

    /**
     * Backs the idempotency check (E8-S2-T1 DoD) — {@code idx_recs_user_category_active} allows
     * at most one active (non-dismissed) recommendation per user+category, so the service layer
     * checks this before inserting rather than relying solely on catching the constraint violation.
     */
    public Optional<Recommendation> findActiveByUserAndCategory(UUID userId, Integer categoryId) {
        rlsSession.setCurrentUser(userId);
        String sql = "SELECT " + SELECT_COLUMNS + " FROM recommendations WHERE user_id = ? AND is_dismissed = FALSE AND category_id "
                + (categoryId != null ? "= ?" : "IS NULL");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (categoryId != null) {
            args.add(categoryId);
        }
        return jdbcTemplate.query(sql, rowMapper, args.toArray()).stream().findFirst();
    }

    /** E8-S2-T2 feed — newest-first, active only, via {@code idx_recs_user_active}. */
    public List<Recommendation> findActiveByUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM recommendations WHERE user_id = ? AND is_dismissed = FALSE ORDER BY generated_at DESC",
                rowMapper,
                userId);
    }

    public Optional<Recommendation> findById(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query("SELECT " + SELECT_COLUMNS + " FROM recommendations WHERE user_id = ? AND id = ?", rowMapper, userId, id)
                .stream()
                .findFirst();
    }

    public void dismiss(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE recommendations SET is_dismissed = TRUE WHERE user_id = ? AND id = ?", userId, id);
    }

    private Recommendation mapRow(ResultSet rs) throws SQLException {
        Object categoryIdObj = rs.getObject("category_id");
        return new Recommendation(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                categoryIdObj == null ? null : (Integer) categoryIdObj,
                rs.getString("text"),
                RecommendationPriority.fromDbValue(rs.getString("priority")),
                rs.getTimestamp("generated_at").toInstant(),
                rs.getBoolean("is_dismissed"));
    }
}
