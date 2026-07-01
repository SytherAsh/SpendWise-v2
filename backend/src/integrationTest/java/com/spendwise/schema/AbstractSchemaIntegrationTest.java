package com.spendwise.schema;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Shared Testcontainers Postgres + Flyway setup for the db/migration schema tests
 * (see implementation/epics/epic-00-foundation.md E0-S2-T2..T6).
 *
 * <p>Migrations run as {@link #APP_USERNAME}, a plain LOGIN role with no
 * superuser/BYPASSRLS attribute -- not the container's default superuser --
 * so that {@code spendwise_app} owns every table and FORCE ROW LEVEL SECURITY
 * (V5__row_level_security.sql) genuinely applies. See backend/db-init/01-app-role.sql
 * for the same reasoning applied to local Docker Compose.
 *
 * <p>{@link #jdbcTemplate} connects as the container's superuser instead,
 * which always bypasses RLS regardless of table ownership -- appropriate for
 * fixture setup and the V1-V4 CHECK/UNIQUE constraint tests, which exercise
 * constraints, not RLS. V5RowLevelSecurityIntegrationTest opens its own
 * connection as {@link #APP_USERNAME}, since RLS behavior is exactly what it verifies.
 */
@Testcontainers
abstract class AbstractSchemaIntegrationTest {

    static final String APP_USERNAME = "spendwise_app";
    static final String APP_PASSWORD = "spendwise_app_password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + APP_USERNAME + " WITH LOGIN PASSWORD '" + APP_PASSWORD + "'");
            statement.execute(
                    "GRANT ALL PRIVILEGES ON DATABASE " + POSTGRES.getDatabaseName() + " TO " + APP_USERNAME);
            statement.execute("GRANT ALL ON SCHEMA public TO " + APP_USERNAME);
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), APP_USERNAME, APP_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
