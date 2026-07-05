package com.spendwise.admin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers E11-S1-T1's Required Test: admin login succeeds and the resulting token works on an
 * admin route and fails on a user route. The rate-limit trip is covered separately in {@link
 * AdminAuthRateLimitIntegrationTest} — its own class so the shared in-memory rate-limiter bucket
 * isn't polluted by these tests' login attempts (or vice versa). Requires Docker — run via
 * {@code ./gradlew integrationTest}.
 *
 * <p>The admin-route smoke check below hits {@code /admin/logs}, which reads via {@code
 * AdminRepository}'s {@code spendwise_jobs} (BYPASSRLS) role — doesn't exist in a bare
 * Testcontainers Postgres image, so {@link #createJobsRole()} creates it directly, mirroring
 * {@code CategorizationJobsIntegrationTest}'s identical bootstrap for the same reason.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthControllerIntegrationTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "correct-horse-battery-staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void createJobsRole() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE spendwise_jobs WITH LOGIN PASSWORD 'spendwise_jobs_password' BYPASSRLS");
            statement.execute("GRANT " + POSTGRES.getUsername() + " TO spendwise_jobs");
        }
    }

    @DynamicPropertySource
    static void adminCredentials(DynamicPropertyRegistry registry) {
        registry.add("app.security.admin-username", () -> ADMIN_USERNAME);
        registry.add("app.security.admin-password-hash", () -> new BCryptPasswordEncoder().encode(ADMIN_PASSWORD));
    }

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void loginSucceedsAndTheIssuedTokenWorksOnAnAdminRouteAndFailsOnAUserRoute() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/admin/auth/login", Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = (String) login.getBody().get("accessToken");
        assertThat(accessToken).isNotBlank();
        assertThat(((Number) login.getBody().get("expiresIn")).longValue()).isEqualTo(86_400L);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // /admin/logs returns a JSON array (List<AdminLogResponse>), not an object — List.class, not Map.class.
        ResponseEntity<List> adminRoute =
                restTemplate.exchange(baseUrl() + "/admin/logs", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(adminRoute.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> userRoute =
                restTemplate.exchange(baseUrl() + "/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(userRoute.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/admin/auth/login", Map.of("username", ADMIN_USERNAME, "password", "wrong-password"), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginWithUnknownUsernameIsRejected() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/admin/auth/login", Map.of("username", "not-the-admin", "password", ADMIN_PASSWORD), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
