package com.spendwise.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers E11-S1-T1's rate-limit requirement (docs/security.md "Login attempts: max 10 per IP per
 * 15 min") in its own class/Spring context so the in-memory {@link AdminLoginRateLimiter} bucket
 * starts fresh — sharing a class with other login tests would make the exact attempt count that
 * trips the limit depend on JUnit's (unspecified) method execution order. Requires Docker — run
 * via {@code ./gradlew integrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthRateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void eleventhLoginAttemptFromTheSameClientWithin15MinutesIsRateLimited() {
        for (int i = 0; i < 10; i++) {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl() + "/admin/auth/login", Map.of("username", "admin", "password", "wrong-password"), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<Map> eleventh = restTemplate.postForEntity(
                baseUrl() + "/admin/auth/login", Map.of("username", "admin", "password", "wrong-password"), Map.class);

        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
