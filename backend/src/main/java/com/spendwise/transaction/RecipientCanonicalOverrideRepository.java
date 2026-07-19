package com.spendwise.transaction;

import com.spendwise.common.db.RlsSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Backs {@code recipient_canonicalization_overrides} (ADR-014). Same dual-datasource shape as
 * {@link MlCorrectionRepository}: the user-triggered upsert goes through the RLS-scoped {@code
 * jdbcTemplate}, while {@link RecipientCanonicalizationSweep}'s cross-user read bypasses RLS via
 * {@code spendwise_jobs}.
 */
@Repository
public class RecipientCanonicalOverrideRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final JdbcTemplate jobsJdbcTemplate;

    public RecipientCanonicalOverrideRepository(
            JdbcTemplate jdbcTemplate, RlsSession rlsSession, @Qualifier("jobsJdbcTemplate") JdbcTemplate jobsJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
        this.jobsJdbcTemplate = jobsJdbcTemplate;
    }

    /**
     * Delete-then-insert rather than {@code ON CONFLICT}: {@code recipient_name}/{@code upi_id}
     * are both nullable and Postgres UNIQUE constraints treat NULLs as distinct, so there is no
     * constraint to conflict on. {@code IS NOT DISTINCT FROM} matches the same null-safe identity
     * comparison {@link TransactionRepository#updateCanonicalForIdentityAsUser} uses.
     */
    public void upsert(UUID userId, String recipientName, String upiId, String canonicalName) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update(
                "DELETE FROM recipient_canonicalization_overrides "
                        + "WHERE user_id = ? AND recipient_name IS NOT DISTINCT FROM ? AND upi_id IS NOT DISTINCT FROM ?",
                userId,
                recipientName,
                upiId);
        jdbcTemplate.update(
                "INSERT INTO recipient_canonicalization_overrides "
                        + "(id, user_id, recipient_name, upi_id, canonical_name, corrected_at) VALUES (?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID(),
                userId,
                recipientName,
                upiId,
                canonicalName);
    }

    /**
     * Cross-user (ADR-014) — every user-pinned override across all users. Backs {@code
     * RecipientCanonicalizationSweep}, which lets a correction permanently win over the ML
     * service's answer on every subsequent weekly resweep; bypasses RLS via the {@code
     * spendwise_jobs} role, same pattern as {@link TransactionRepository#findAllRecipientIdentities}.
     */
    public List<RecipientCanonicalOverride> findAll() {
        return jobsJdbcTemplate.query(
                "SELECT user_id, recipient_name, upi_id, canonical_name FROM recipient_canonicalization_overrides",
                (rs, rowNum) -> new RecipientCanonicalOverride(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("recipient_name"),
                        rs.getString("upi_id"),
                        rs.getString("canonical_name")));
    }
}
