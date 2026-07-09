package com.spendwise.user;

import com.spendwise.common.db.RlsSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All methods here must be called from within a {@code @Transactional} method so that the RLS
 * session variable set by {@link RlsSession} and the query that depends on it share the same
 * connection.
 */
@Repository
public class ContactRepository {

    private static final RowMapper<Contact> ROW_MAPPER = (rs, rowNum) -> new Contact(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("user_id")),
            rs.getString("name"),
            Contact.RelationshipType.fromDbValue(rs.getString("relationship_type")),
            rs.getString("recipient_name_pattern"),
            rs.getString("upi_id"),
            rs.getString("phone_number"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;
    private final RlsSession rlsSession;

    public ContactRepository(JdbcTemplate jdbcTemplate, RlsSession rlsSession) {
        this.jdbcTemplate = jdbcTemplate;
        this.rlsSession = rlsSession;
    }

    public Contact insert(
            UUID userId,
            String name,
            Contact.RelationshipType relationshipType,
            String recipientNamePattern,
            String upiId,
            String phoneNumber) {
        rlsSession.setCurrentUser(userId);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO contacts (id, user_id, name, relationship_type, recipient_name_pattern, upi_id, phone_number) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                name,
                relationshipType.dbValue(),
                recipientNamePattern,
                upiId,
                phoneNumber);
        return find(userId, id).orElseThrow();
    }

    public List<Contact> findAllForUser(UUID userId) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query("SELECT * FROM contacts WHERE user_id = ? ORDER BY name", ROW_MAPPER, userId);
    }

    public Optional<Contact> find(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.query("SELECT * FROM contacts WHERE id = ? AND user_id = ?", ROW_MAPPER, id, userId).stream()
                .findFirst();
    }

    public Optional<Contact> update(
            UUID userId,
            UUID id,
            String name,
            Contact.RelationshipType relationshipType,
            String recipientNamePattern,
            String upiId,
            String phoneNumber) {
        rlsSession.setCurrentUser(userId);
        int updated = jdbcTemplate.update(
                "UPDATE contacts SET name = ?, relationship_type = ?, recipient_name_pattern = ?, upi_id = ?, phone_number = ? "
                        + "WHERE id = ? AND user_id = ?",
                name,
                relationshipType.dbValue(),
                recipientNamePattern,
                upiId,
                phoneNumber,
                id,
                userId);
        return updated == 0 ? Optional.empty() : find(userId, id);
    }

    public boolean delete(UUID userId, UUID id) {
        rlsSession.setCurrentUser(userId);
        return jdbcTemplate.update("DELETE FROM contacts WHERE id = ? AND user_id = ?", id, userId) > 0;
    }
}
