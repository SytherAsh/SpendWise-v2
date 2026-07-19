package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Backs {@code recipient_merge_suggestions} (Merge Payees feature, ML strategy phase,
 * 2026-07-19). Same dual-datasource shape as {@link RecipientCanonicalOverrideRepository}: the
 * per-user queue read/resolve go through the RLS-scoped {@code jdbcTemplate}, while {@code
 * RecipientCanonicalizationSweep}'s cross-user dedup read and insert bypass RLS via {@code
 * spendwise_jobs}.
 */
@Repository
public class RecipientMergeSuggestionRepository {

    private static final RowMapper<RecipientMergeSuggestion> ROW_MAPPER = (rs, rowNum) -> new RecipientMergeSuggestion(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getString("anchor_name"),
            rs.getString("anchor_upi_id"),
            rs.getString("anchor_canonical_name"),
            rs.getString("candidate_name"),
            rs.getString("candidate_upi_id"),
            rs.getInt("score"),
            rs.getString("reason"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant());

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final JdbcTemplate jobsJdbcTemplate;

    public RecipientMergeSuggestionRepository(
            JdbcTemplate jdbcTemplate, RlsSession rlsSession, @Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.jobsJdbcTemplate = jobsJdbcTemplate;
    }

    /** Per-user pending queue (Merge Payees) — oldest first, so the same group keeps surfacing
     * consistently across repeated fetches until resolved. RLS-scoped. */
    public List<RecipientMergeSuggestion> findPendingForUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT id, user_id, anchor_name, anchor_upi_id, anchor_canonical_name, candidate_name, "
                        + "candidate_upi_id, score, reason, status, created_at, resolved_at "
                        + "FROM recipient_merge_suggestions WHERE user_id = ? AND status = ? ORDER BY created_at",
                ROW_MAPPER,
                userId,
                RecipientMergeSuggestion.STATUS_PENDING);
    }

    /** Flips status for a batch of this user's own suggestions. RLS-scoped (via the explicit
     * user_id in the WHERE clause) so a foreign suggestion id is silently skipped, not an error —
     * same defensive shape as every other per-user write in this module. */
    public void resolveMany(UUID userId, List<UUID> suggestionIds, String status) {
        rlsSession.setCurrentUser(userId);
        for (UUID id : suggestionIds) {
            jdbcTemplate.update(
                    "UPDATE recipient_merge_suggestions SET status = ?, resolved_at = NOW() WHERE user_id = ? AND id = ?",
                    status,
                    userId,
                    id);
        }
    }

    /**
     * Cross-user (Merge Payees) — every (anchor, candidate) identity pair already suggested for
     * this user, in ANY status, normalized to an unordered key so {@code
     * RecipientCanonicalizationSweep} never re-suggests a pair the algorithm flipped anchor/
     * candidate roles on between resweeps. Scoped to one user per call (the sweep already
     * iterates user-by-user) via the {@code spendwise_jobs} role.
     */
    public Set<UnorderedPairKey> findExistingPairsForUser(UUID userId) {
        List<UnorderedPairKey> rows = jobsJdbcTemplate.query(
                "SELECT anchor_name, anchor_upi_id, candidate_name, candidate_upi_id "
                        + "FROM recipient_merge_suggestions WHERE user_id = ?",
                (rs, rowNum) -> UnorderedPairKey.of(
                        rs.getString("anchor_name"),
                        rs.getString("anchor_upi_id"),
                        rs.getString("candidate_name"),
                        rs.getString("candidate_upi_id")),
                userId);
        return Set.copyOf(rows);
    }

    /** Cross-user batch insert (Merge Payees) — new PENDING suggestions found by this resweep,
     * via the spendwise_jobs role. Caller (the sweep) must have already excluded anything {@link
     * #findExistingPairsForUser} already covers. */
    public void insertPending(UUID userId, List<NewMergeSuggestion> suggestions) {
        for (NewMergeSuggestion s : suggestions) {
            jobsJdbcTemplate.update(
                    "INSERT INTO recipient_merge_suggestions "
                            + "(id, user_id, anchor_name, anchor_upi_id, anchor_canonical_name, candidate_name, "
                            + "candidate_upi_id, score, reason, status, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    UUID.randomUUID(),
                    userId,
                    s.anchorName(),
                    s.anchorUpiId(),
                    s.anchorCanonicalName(),
                    s.candidateName(),
                    s.candidateUpiId(),
                    s.score(),
                    s.reason(),
                    RecipientMergeSuggestion.STATUS_PENDING);
        }
    }
}
