package com.spendwise.schema;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required tests for E0-S2-T6, per docs/security.md "Supabase Row-Level Security":
 * (1) a query without app.current_user_id set returns zero rows (safe-fail deny);
 * (2) a query with app.current_user_id set to user A only sees user A's rows,
 * even though user B's rows exist in the same table.
 *
 * <p>Uses its own connection as {@link #APP_USERNAME} (not the shared
 * superuser {@code jdbcTemplate} from the base class) because RLS behavior is
 * exactly what's under test here -- fixture rows are inserted via the
 * superuser connection, which bypasses RLS, so fixture setup itself isn't
 * blocked by the policies being verified.
 *
 * <p>set_config(..., true) is transaction-scoped, so both the set_config call
 * and the subsequent query must run in the same explicit transaction (hence
 * autoCommit(false) + an explicit rollback rather than relying on JdbcTemplate's
 * one-connection-per-call convenience, which would open a fresh transaction
 * per statement and lose the setting).
 */
class V5RowLevelSecurityIntegrationTest extends AbstractSchemaIntegrationTest {

    @Test
    void queryWithoutSessionVariableReturnsNoRows() throws Exception {
        UUID userA = insertTestUser();
        insertTestTransaction(userA, "txn-rls-no-session-var");

        try (Connection connection = appConnection()) {
            connection.setAutoCommit(false);
            try (ResultSet resultSet =
                    connection.createStatement().executeQuery("SELECT count(*) FROM transactions")) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isZero();
            }
            connection.rollback();
        }
    }

    @Test
    void queryWithSessionVariableOnlySeesOwnRows() throws Exception {
        UUID userA = insertTestUser();
        UUID userB = insertTestUser();
        insertTestTransaction(userA, "txn-rls-user-a");
        insertTestTransaction(userB, "txn-rls-user-b");

        try (Connection connection = appConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement setConfig =
                    connection.prepareStatement("SELECT set_config('app.current_user_id', ?, true)")) {
                setConfig.setString(1, userA.toString());
                setConfig.execute();
            }

            try (ResultSet resultSet =
                    connection.createStatement().executeQuery("SELECT user_id FROM transactions")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(userA.toString());
                assertThat(resultSet.next()).isFalse(); // user B's row must not be visible
            }
            connection.rollback();
        }
    }

    private Connection appConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USERNAME, APP_PASSWORD);
    }

    private UUID insertTestUser() {
        UUID userId = UUID.randomUUID();
        // Via the superuser jdbcTemplate: fixture setup, not the behavior under test.
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, ?)", userId, userId.toString());
        return userId;
    }

    private void insertTestTransaction(UUID userId, String transactionId) {
        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, transaction_date, debit, credit, amount, dr_cr_indicator,"
                        + " transaction_id, source) VALUES (?, NOW(), 199, 0, -199, 'DR', ?,"
                        + " 'sms'::transaction_source)",
                userId,
                transactionId);
    }
}
