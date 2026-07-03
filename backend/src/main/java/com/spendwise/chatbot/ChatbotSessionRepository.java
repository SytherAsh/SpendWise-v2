package com.spendwise.chatbot;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so the RLS session
 * variable and the query that depends on it share the same connection (mirrors {@code
 * com.spendwise.alerts.AlertRepository}).
 */
@Repository
public class ChatbotSessionRepository {

    private static final String SELECT_COLUMNS = "id, user_id, created_at, last_active_at";

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final RowMapper<ChatbotSession> rowMapper = (rs, rowNum) -> new ChatbotSession(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("last_active_at").toInstant());

    public ChatbotSessionRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public ChatbotSession insert(UUID userId) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO chatbot_sessions (id, user_id) VALUES (?, ?) RETURNING " + SELECT_COLUMNS, rowMapper, id, userId);
    }

    /** docs/api.md "/chatbot/sessions" — ordered by {@code last_active_at DESC}. */
    public List<ChatbotSession> findByUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM chatbot_sessions WHERE user_id = ? ORDER BY last_active_at DESC", rowMapper, userId);
    }

    public Optional<ChatbotSession> findById(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate
                .query("SELECT " + SELECT_COLUMNS + " FROM chatbot_sessions WHERE user_id = ? AND id = ?", rowMapper, userId, id)
                .stream()
                .findFirst();
    }

    public void touchLastActive(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        jdbcTemplate.update("UPDATE chatbot_sessions SET last_active_at = NOW() WHERE user_id = ? AND id = ?", userId, id);
    }
}
