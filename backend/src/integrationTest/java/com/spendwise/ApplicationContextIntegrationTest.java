package com.spendwise;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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

    @Test
    void contextLoads() {
        // Intentionally empty: a successful load (Flyway migrating against a
        // real Postgres container included) is the assertion.
    }
}
