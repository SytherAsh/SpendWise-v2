package com.spendwise.chatbot;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so the RLS session
 * variable and the query that depends on it share the same connection (mirrors {@code
 * com.spendwise.alerts.AlertRepository}).
 */
@Repository
public class ChatbotConversationRepository {

    private static final String SELECT_COLUMNS = "id, user_id, session_id, role, message, created_at";

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;
    private final RowMapper<ChatbotConversation> rowMapper = (rs, rowNum) -> new ChatbotConversation(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            UUID.fromString(rs.getString("session_id")),
            ChatRole.fromDbValue(rs.getString("role")),
            rs.getString("message"),
            rs.getTimestamp("created_at").toInstant());

    public ChatbotConversationRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public ChatbotConversation insert(UUID userId, UUID sessionId, ChatRole role, String message) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject(
                "INSERT INTO chatbot_conversations (id, user_id, session_id, role, message) VALUES (?, ?, ?, ?::chat_role, ?) "
                        + "RETURNING " + SELECT_COLUMNS,
                rowMapper,
                id,
                userId,
                sessionId,
                role.dbValue(),
                message);
    }

    /** Chronological order (docs/api.md "/chatbot/sessions/:id" full history). */
    public List<ChatbotConversation> findBySession(UUID userId, UUID sessionId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM chatbot_conversations WHERE user_id = ? AND session_id = ? ORDER BY created_at ASC",
                rowMapper,
                userId,
                sessionId);
    }
}
