package com.spendwise.common.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sets the transaction-scoped session variables that back Supabase Row-Level Security
 * (docs/security.md "Supabase Row-Level Security"). Callers must invoke these from within
 * a {@code @Transactional} method so the variable and the query that depends on it share
 * the same connection — {@code set_config(..., true)} is scoped to the current transaction.
 */
@Component
public class RlsSession {

    private final JdbcTemplate jdbcTemplate;

    public RlsSession(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Scopes subsequent queries in this transaction to the given authenticated user. */
    public void setCurrentUser(UUID userId) {
        jdbcTemplate.update("SELECT set_config('app.current_user_id', ?, true)", userId.toString());
    }

    /**
     * Scopes a subsequent pre-authentication lookup on {@code users} to an exact phone
     * number or google_id — see docs/database.md "Auth login lookup addendum".
     */
    public void setAuthLookupIdentifier(String identifier) {
        jdbcTemplate.update("SELECT set_config('app.auth_lookup_identifier', ?, true)", identifier);
    }

    /**
     * Scopes a subsequent pre-authentication lookup on {@code refresh_tokens} to an exact
     * token hash — see docs/database.md "Auth login lookup addendum".
     */
    public void setAuthLookupTokenHash(String tokenHash) {
        jdbcTemplate.update("SELECT set_config('app.auth_lookup_token_hash', ?, true)", tokenHash);
    }
}
