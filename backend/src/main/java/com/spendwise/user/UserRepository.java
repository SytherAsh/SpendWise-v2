package com.spendwise.user;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so that the
 * RLS session variable set by {@link RlsSession} and the query that depends on it share the
 * same connection (docs/security.md "Supabase Row-Level Security").
 */
@Repository
public class UserRepository {

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            UUID.fromString(rs.getString("id")),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("google_id"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public UserRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    /** Pre-authentication lookup — see docs/database.md "Auth login lookup addendum". */
    public Optional<User> findByPhone(String phone) {
        rlsSession.setAuthLookupIdentifier(phone);
        return jdbcTemplate.query("SELECT * FROM users WHERE phone = ?", ROW_MAPPER, phone).stream().findFirst();
    }

    /** Pre-authentication lookup — see docs/database.md "Auth login lookup addendum". */
    public Optional<User> findByGoogleId(String googleId) {
        rlsSession.setAuthLookupIdentifier(googleId);
        return jdbcTemplate.query("SELECT * FROM users WHERE google_id = ?", ROW_MAPPER, googleId).stream().findFirst();
    }

    public Optional<User> findById(UUID id) {
        rlsSession.setCurrentUser(id);
        return jdbcTemplate.query("SELECT * FROM users WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    /**
     * Generates the new user's id and sets the RLS session to it before inserting — V5's
     * {@code users} policy has no WITH CHECK, so USING (id = current_user_id) governs the
     * INSERT too (see docs/database.md).
     */
    public User createWithPhone(String phone) {
        UUID id = UUID.randomUUID();
        rlsSession.setCurrentUser(id);
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, ?)", id, phone);
        return new User(id, phone, null, null, Instant.now());
    }

    public User createWithGoogleId(String googleId, String email) {
        UUID id = UUID.randomUUID();
        rlsSession.setCurrentUser(id);
        jdbcTemplate.update("INSERT INTO users (id, google_id, email) VALUES (?, ?, ?)", id, googleId, email);
        return new User(id, null, email, googleId, Instant.now());
    }

    /** Profile self-edit scope: email is the only user-editable identity field (E1-S3-T1). */
    public void updateEmail(UUID id, String email) {
        rlsSession.setCurrentUser(id);
        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?", email, id);
    }
}
