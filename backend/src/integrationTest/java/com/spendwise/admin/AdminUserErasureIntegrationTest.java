package com.spendwise.admin;

import com.spendwise.auth.FirebaseAuthTestConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers E11-S2-T5's Required Test in full: seed a user with a row in every dependent table (all
 * of Epic 3/5/6/8's schema, per docs/database.md) plus an {@code admin_logs} row whose payload
 * contains an identifying string, delete, and assert every dependent table is empty for that user
 * AND the {@code admin_logs} row is scrubbed (not just null {@code user_id}). Its own class, per
 * the epic's own note to build/test the erasure flow last and in isolation — the most sensitive
 * task in Epic 11. Requires Docker — run via {@code ./gradlew integrationTest}.
 *
 * <p>The erasure flow's {@code jobsJdbcTemplate}-backed delete needs the {@code spendwise_jobs}
 * (BYPASSRLS) role, which doesn't exist in a bare Testcontainers Postgres image —
 * {@link #createJobsRole()} creates it directly, mirroring {@code
 * CategorizationJobsIntegrationTest}'s identical bootstrap for the same reason.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class AdminUserErasureIntegrationTest {

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

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @Value("${app.security.admin-jwt-secret}")
    private String adminJwtSecret;

    private static final String[] DIRECT_USER_ID_TABLES = {
        "user_preferences", "user_consent", "refresh_tokens", "transactions", "budgets", "alerts", "emis",
        "recommendations", "chatbot_sessions", "chatbot_conversations", "device_api_keys"
    };

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void deletingAUserErasesEveryDependentTableAndScrubsIdentifyingStringsFromAdminLogs() throws Exception {
        // FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A always resolves to a fixed identity
        // ("+911111111111") regardless of the phone string in the request body — the token, not
        // the request, determines identity (see AuthControllerIntegrationTest's own note). Fetch
        // the actual persisted phone rather than assuming the request's phone landed in the DB.
        HttpHeaders userHeaders = authHeadersViaOtp("+919555599999");
        UUID userId = currentUserId(userHeaders);
        String phone = fetchUserPhone(userId);

        UUID transactionId = seedEveryDependentTable(userId, phone);
        UUID logId = seedAdminLogReferencingUser(userId, phone);

        // Sanity check the fixture actually landed before asserting erasure removed it.
        assertThat(countWhereUserId("transactions", userId)).isEqualTo(1);
        assertThat(countTransactionCategories(transactionId)).isEqualTo(1);
        assertThat(countMlCorrections(transactionId)).isEqualTo(1);

        ResponseEntity<Void> delete = restTemplate.exchange(
                baseUrl() + "/admin/users/" + userId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(countWhereId("users", userId)).isEqualTo(0);
        for (String table : DIRECT_USER_ID_TABLES) {
            assertThat(countWhereUserId(table, userId)).as("table " + table).isEqualTo(0);
        }
        assertThat(countTransactionCategories(transactionId)).isEqualTo(0);
        assertThat(countMlCorrections(transactionId)).isEqualTo(0);

        Map<String, String> scrubbedRow = fetchAdminLog(logId);
        assertThat(scrubbedRow.get("user_id")).isNull();
        assertThat(scrubbedRow.get("event_type")).doesNotContain(phone);
        assertThat(scrubbedRow.get("payload")).doesNotContain(phone);
    }

    @Test
    void deletingANonexistentUserReturns404() {
        ResponseEntity<Map> delete = restTemplate.exchange(
                baseUrl() + "/admin/users/" + UUID.randomUUID(), HttpMethod.DELETE, new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Seeds one row per user_id-referencing table (docs/database.md), plus transaction_categories/ml_corrections keyed off the seeded transaction. */
    private UUID seedEveryDependentTable(UUID userId, String phone) throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO user_preferences (user_id) VALUES ('" + userId + "') ON CONFLICT DO NOTHING");
            statement.execute("INSERT INTO user_consent (id, user_id, consent_text) VALUES ('" + UUID.randomUUID() + "', '" + userId
                    + "', 'consent text fixture')");
            statement.execute("INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES ('" + UUID.randomUUID() + "', '"
                    + userId + "', 'fixture-hash-" + UUID.randomUUID() + "', NOW() + INTERVAL '30 days')");
            statement.execute("INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, dr_cr_indicator, "
                    + "transaction_id, source) VALUES ('" + transactionId + "', '" + userId
                    + "', '2026-01-15T10:00:00Z', 500, 0, -500, 'DR', 'erasure-fixture-" + transactionId + "', 'sms')");
            statement.execute("INSERT INTO transaction_categories (transaction_id, category_id, assigned_by) VALUES ('" + transactionId
                    + "', 1, 'user')");
            statement.execute("INSERT INTO ml_corrections (id, transaction_id, new_category_id) VALUES ('" + UUID.randomUUID() + "', '"
                    + transactionId + "', 2)");
            statement.execute("INSERT INTO budgets (id, user_id, category_id, monthly_limit, month, year) VALUES ('" + UUID.randomUUID()
                    + "', '" + userId + "', 1, 2000, 1, 2026)");
            statement.execute("INSERT INTO alerts (id, user_id, type, priority, payload) VALUES ('" + UUID.randomUUID() + "', '" + userId
                    + "', 'category_overspend', 'high', '{}'::jsonb)");
            statement.execute("INSERT INTO emis (id, user_id, label, amount, source_transaction_id) VALUES ('" + UUID.randomUUID() + "', '"
                    + userId + "', 'Erasure Fixture EMI', 1000, NULL)");
            statement.execute("INSERT INTO recommendations (id, user_id, text, priority) VALUES ('" + UUID.randomUUID() + "', '" + userId
                    + "', 'fixture recommendation', 'medium')");
            statement.execute("INSERT INTO chatbot_sessions (id, user_id) VALUES ('" + sessionId + "', '" + userId + "')");
            statement.execute("INSERT INTO chatbot_conversations (id, user_id, session_id, role, message) VALUES ('" + UUID.randomUUID()
                    + "', '" + userId + "', '" + sessionId + "', 'user', 'fixture message')");
            statement.execute("INSERT INTO device_api_keys (id, user_id, key_hash) VALUES ('" + UUID.randomUUID() + "', '" + userId
                    + "', 'fixture-key-hash-" + UUID.randomUUID() + "')");
        }
        return transactionId;
    }

    private UUID seedAdminLogReferencingUser(UUID userId, String phone) throws Exception {
        UUID logId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO admin_logs (id, event_type, user_id, payload) VALUES ('" + logId
                    + "', 'sync_error', '" + userId + "', '{\"phone\":\"" + phone + "\",\"reason\":\"device offline\"}'::jsonb)");
        }
        return logId;
    }

    private String fetchUserPhone(UUID userId) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT phone FROM users WHERE id = '" + userId + "'")) {
            rs.next();
            return rs.getString("phone");
        }
    }

    private int countWhereUserId(String table, UUID userId) throws Exception {
        return countWhere(table, "user_id = '" + userId + "'");
    }

    private int countWhereId(String table, UUID id) throws Exception {
        return countWhere(table, "id = '" + id + "'");
    }

    private int countTransactionCategories(UUID transactionId) throws Exception {
        return countWhere("transaction_categories", "transaction_id = '" + transactionId + "'");
    }

    private int countMlCorrections(UUID transactionId) throws Exception {
        return countWhere("ml_corrections", "transaction_id = '" + transactionId + "'");
    }

    private int countWhere(String table, String predicate) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + predicate)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** {@code Map.of} rejects null values, and a genuinely-null {@code user_id} is exactly what this test must be able to assert on — a plain {@link HashMap} instead. */
    private Map<String, String> fetchAdminLog(UUID logId) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT user_id, event_type, payload FROM admin_logs WHERE id = '" + logId + "'")) {
            rs.next();
            Map<String, String> row = new HashMap<>();
            row.put("user_id", rs.getString("user_id"));
            row.put("event_type", rs.getString("event_type"));
            row.put("payload", rs.getString("payload"));
            return row;
        }
    }

    private HttpHeaders authHeadersViaOtp(String phone) {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        return bearerHeaders((String) verify.getBody().get("accessToken"));
    }

    private HttpHeaders adminHeaders() {
        String token = Jwts.builder()
                .subject("admin-1")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(adminJwtSecret.getBytes()))
                .compact();
        return bearerHeaders(token);
    }

    private static HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private UUID currentUserId(HttpHeaders headers) {
        String accessToken = headers.getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length());
        String[] parts = accessToken.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String subject = payloadJson.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
        return UUID.fromString(subject);
    }
}
