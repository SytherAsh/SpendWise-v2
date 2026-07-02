package com.spendwise;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moved here (from src/test) once the app gained a real DataSource/Flyway:
 * a full context load now touches the database, which belongs in the
 * Testcontainers-backed integration suite, not the mock-only unit suite.
 * See docs/testing.md "Why Testcontainers".
 */
@Testcontainers
@SpringBootTest
class ApplicationContextIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
        // Intentionally empty: a successful load (Flyway migrating against a
        // real Postgres container included) is the assertion.
    }

    /**
     * Regression test for a real incident (Epic 4 close-out, 2026-07-02): Spring Boot's
     * auto-configured {@code JdbcTemplate} backs off entirely once any OTHER {@code JdbcTemplate}
     * bean exists in the context ({@code @ConditionalOnMissingBean(JdbcOperations.class)}) — so
     * without an explicit {@code @Primary JdbcTemplate} bean in {@code
     * com.spendwise.common.db.JobsDataSourceConfig}, every unqualified {@code JdbcTemplate}
     * injection across the whole app (every repository that doesn't explicitly ask for {@code
     * @Qualifier("jobsJdbcTemplate")}) silently received the {@code BYPASSRLS}-enabled {@code
     * spendwise_jobs} connection instead of the RLS-enforced one — every real request failed
     * outright in CI only because {@code spendwise_jobs} didn't exist in the test container; in
     * an environment where the role DOES exist, this would have silently defeated the RLS
     * backstop app-wide instead.
     */
    @Test
    void unqualifiedJdbcTemplateIsNotTheJobsBypassRlsPool() {
        String currentUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        assertThat(currentUser).isNotEqualTo("spendwise_jobs");
    }
}
