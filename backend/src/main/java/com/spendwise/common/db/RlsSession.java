package com.spendwise.common.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sets the transaction-scoped session variables that back Supabase Row-Level Security
 * (docs/security.md "Supabase Row-Level Security"). Callers must invoke these from within
 * a {@code @Transactional} method so the variable and the query that depends on it share
 * the same connection — {@code set_config(..., true)} is scoped to the current transaction.
 *
 * <p>{@code SELECT set_config(...)} is a query (it returns the set value as a one-row,
 * one-column result set), not a DML statement — {@link JdbcTemplate#queryForObject} is used
 * rather than {@link JdbcTemplate#update}, which calls {@code PreparedStatement.executeUpdate()}
 * and makes the PostgreSQL JDBC driver reject any statement that returns a result set with
 * {@code PSQLException: A result was returned when none was expected.}
 */
@Component
public class RlsSession {

    private final JdbcTemplate jdbcTemplate;

    public RlsSession(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Scopes subsequent queries in this transaction to the given authenticated user. */
    public void setCurrentUser(UUID userId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.current_user_id', ?, true)", String.class, userId.toString());
    }

    /**
     * Scopes a subsequent pre-authentication lookup on {@code users} to an exact phone
     * number or google_id — see docs/database.md "Auth login lookup addendum".
     */
    public void setAuthLookupIdentifier(String identifier) {
        jdbcTemplate.queryForObject("SELECT set_config('app.auth_lookup_identifier', ?, true)", String.class, identifier);
    }

    /**
     * Scopes a subsequent pre-authentication lookup on {@code refresh_tokens} to an exact
     * token hash — see docs/database.md "Auth login lookup addendum".
     */
    public void setAuthLookupTokenHash(String tokenHash) {
        jdbcTemplate.queryForObject("SELECT set_config('app.auth_lookup_token_hash', ?, true)", String.class, tokenHash);
    }
}
