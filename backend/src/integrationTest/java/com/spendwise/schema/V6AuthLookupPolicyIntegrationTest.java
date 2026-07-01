package com.spendwise.schema;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required tests for the V6 addendum described in docs/database.md "Auth login lookup
 * addendum" and docs/security.md "Auth login lookup policy": (1) a lookup by the exact
 * phone number, with app.auth_lookup_identifier set and no app.current_user_id set,
 * succeeds; (2) a lookup for a phone number that was never set as the identifier returns
 * no rows (no enumeration); (3) the pre-existing V5 owner-only policy is untouched --
 * setting app.current_user_id still permits access by id alone.
 */
class V6AuthLookupPolicyIntegrationTest extends AbstractSchemaIntegrationTest {

    @Test
    void lookupByExactPhoneSucceedsWithoutCurrentUserId() throws Exception {
        UUID userId = insertTestUser("+911234567890");

        try (Connection connection = appConnection()) {
            connection.setAutoCommit(false);
            setSessionVar(connection, "app.auth_lookup_identifier", "+911234567890");

            try (ResultSet resultSet =
                    connection.createStatement().executeQuery("SELECT id FROM users WHERE phone = '+911234567890'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(userId.toString());
            }
            connection.rollback();
        }
    }

    @Test
    void lookupWithoutMatchingIdentifierReturnsNoRows() throws Exception {
        insertTestUser("+911111111111");

        try (Connection connection = appConnection()) {
            connection.setAutoCommit(false);
            setSessionVar(connection, "app.auth_lookup_identifier", "+919999999999");

            try (ResultSet resultSet =
                    connection.createStatement().executeQuery("SELECT id FROM users WHERE phone = '+911111111111'")) {
                assertThat(resultSet.next()).isFalse();
            }
            connection.rollback();
        }
    }

    @Test
    void ownerOnlyPolicyFromV5StillAppliesByCurrentUserId() throws Exception {
        UUID userId = insertTestUser("+912222222222");

        try (Connection connection = appConnection()) {
            connection.setAutoCommit(false);
            setSessionVar(connection, "app.current_user_id", userId.toString());

            try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT id FROM users")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(userId.toString());
                assertThat(resultSet.next()).isFalse();
            }
            connection.rollback();
        }
    }

    @Test
    void refreshTokenLookupByExactHashSucceedsWithoutCurrentUserId() throws Exception {
        UUID userId = insertTestUser("+913333333333");
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, NOW() + INTERVAL '30 days')",
                userId,
                "hash-abc123");

        try (Connection connection = appConnection()) {
            connection.setAutoCommit(false);
            setSessionVar(connection, "app.auth_lookup_token_hash", "hash-abc123");

            try (ResultSet resultSet = connection.createStatement()
                    .executeQuery("SELECT user_id FROM refresh_tokens WHERE token_hash = 'hash-abc123'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(userId.toString());
            }
            connection.rollback();
        }
    }

    @Test
    void refreshTokenLookupWithoutMatchingHashReturnsNoRows() throws Exception {
        UUID userId = insertTestUser("+914444444444");
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, NOW() + INTERVAL '30 days')",
                userId,
                "hash-real");

        try (Connection connection = appConnection()) {
            connection.setAutoCommit(false);
            setSessionVar(connection, "app.auth_lookup_token_hash", "hash-guessed");

            try (ResultSet resultSet = connection.createStatement()
                    .executeQuery("SELECT user_id FROM refresh_tokens WHERE token_hash = 'hash-real'")) {
                assertThat(resultSet.next()).isFalse();
            }
            connection.rollback();
        }
    }

    private Connection appConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USERNAME, APP_PASSWORD);
    }

    private void setSessionVar(Connection connection, String key, String value) throws Exception {
        try (PreparedStatement setConfig =
                connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            setConfig.setString(1, key);
            setConfig.setString(2, value);
            setConfig.execute();
        }
    }

    private UUID insertTestUser(String phone) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, ?)", userId, phone);
        return userId;
    }
}
