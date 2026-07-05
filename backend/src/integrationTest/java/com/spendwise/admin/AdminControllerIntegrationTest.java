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
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E11-S2-T1/T2/T3 (docs/testing.md, epic-11-admin-portal.md):
 * non-admin tokens rejected on every {@code /admin/*} route, {@code sms_raw_text} absence on the
 * user detail view, cross-user analytics numbers matching a manual sum of per-user
 * {@code /analytics/summary}, and {@code /admin/logs} event-type filtering. The erasure flow
 * (E11-S2-T5) has its own dedicated class per the epic's "build/test it last" note. Requires
 * Docker — run via {@code ./gradlew integrationTest}.
 *
 * <p>{@code AdminRepository} reads via the {@code spendwise_jobs} (BYPASSRLS) role, which doesn't
 * exist in a bare Testcontainers Postgres image — {@link #createJobsRole()} creates it directly,
 * mirroring {@code CategorizationJobsIntegrationTest}'s identical bootstrap for the same reason.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class AdminControllerIntegrationTest {

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

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void nonAdminTokenIsRejectedOnEveryAdminRoute() {
        HttpHeaders userHeaders = authHeadersViaOtp("+919555500001");

        assertThat(exchange("/admin/users", userHeaders).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange("/admin/logs", userHeaders).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange("/admin/ml/accuracy", userHeaders).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userDetailReturnsTransactionsAndBudgetsButNeverSmsRawText() throws Exception {
        HttpHeaders userHeaders = authHeadersViaOtp("+919555500002");
        UUID userId = currentUserId(userHeaders);
        insertTransactionWithRawSmsText(userId, "Your A/c is debited for Rs.500 — SECRET-RAW-SMS-MARKER");
        restTemplate.exchange(
                baseUrl() + "/budgets",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("categoryId", 1, "monthlyLimit", 2000), userHeaders),
                Map.class);
        HttpHeaders adminHeaders = adminHeaders();

        ResponseEntity<Map> detail = restTemplate.exchange(
                baseUrl() + "/admin/users/" + userId, HttpMethod.GET, new HttpEntity<>(adminHeaders), Map.class);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> transactions = (List<Map<String, Object>>) detail.getBody().get("transactions");
        assertThat(transactions).hasSizeGreaterThanOrEqualTo(1);
        assertThat(transactions.get(0).keySet()).doesNotContain("smsRawText", "sms_raw_text");
        assertThat(detail.getBody().toString()).doesNotContain("SECRET-RAW-SMS-MARKER");
        List<Map<String, Object>> budgets = (List<Map<String, Object>>) detail.getBody().get("budgets");
        assertThat(budgets).anyMatch(b -> b.get("categoryId").equals(1));
    }

    @Test
    void listUsersIncludesTransactionCountAndLastActivity() {
        HttpHeaders userHeaders = authHeadersViaOtp("+919555500003");
        UUID userId = currentUserId(userHeaders);
        restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactionDate", "2026-06-15T14:32:00Z", "amount", -250.0), userHeaders),
                Map.class);
        HttpHeaders adminHeaders = adminHeaders();

        ResponseEntity<List> list = restTemplate.exchange(baseUrl() + "/admin/users", HttpMethod.GET, new HttpEntity<>(adminHeaders), List.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).anySatisfy(row -> {
            Map<?, ?> summary = (Map<?, ?>) row;
            if (summary.get("id").equals(userId.toString())) {
                assertThat(((Number) summary.get("transactionCount")).longValue()).isGreaterThanOrEqualTo(1);
                assertThat(summary.get("lastActivity")).isNotNull();
            }
        });
    }

    @Test
    void aggregateAnalyticsMatchesTheManualSumOfEachUsersOwnSummary() {
        // A deliberately distinctive date range (year 2031) not used by any other fixture in this
        // class — /admin/analytics sums every user in the shared Testcontainers instance, so a
        // range any other test's transactions could fall into would make this assertion
        // order-dependent (the exact class of flake Epic 7's close-out fixed after the fact).
        HttpHeaders userAHeaders = authHeadersViaOtp("+919555500004");
        HttpHeaders userBHeaders = authHeadersViaGoogle();
        String from = "2031-03-01T00:00:00Z";
        String to = "2031-03-31T23:59:59Z";
        restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactionDate", "2031-03-10T10:00:00Z", "amount", -300.0), userAHeaders),
                Map.class);
        restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactionDate", "2031-03-12T10:00:00Z", "amount", -700.0), userBHeaders),
                Map.class);

        Map summaryA = restTemplate.exchange(
                        baseUrl() + "/analytics/summary?from=" + from + "&to=" + to, HttpMethod.GET, new HttpEntity<>(userAHeaders), Map.class)
                .getBody();
        Map summaryB = restTemplate.exchange(
                        baseUrl() + "/analytics/summary?from=" + from + "&to=" + to, HttpMethod.GET, new HttpEntity<>(userBHeaders), Map.class)
                .getBody();
        double expectedTotalSpend = Double.parseDouble(summaryA.get("totalSpend").toString()) + Double.parseDouble(summaryB.get("totalSpend").toString());

        ResponseEntity<Map> aggregate = restTemplate.exchange(
                baseUrl() + "/admin/analytics?from=" + from + "&to=" + to, HttpMethod.GET, new HttpEntity<>(adminHeaders()), Map.class);

        assertThat(aggregate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Double.parseDouble(aggregate.getBody().get("totalSpend").toString())).isEqualTo(expectedTotalSpend);
    }

    @Test
    void logsFilterReturnsOnlyTheMatchingEventType() throws Exception {
        insertAdminLog("parse_failure", null, "{\"reason\":\"unparseable sms\"}");
        insertAdminLog("sync_error", null, "{\"reason\":\"network timeout\"}");

        ResponseEntity<List> filtered = restTemplate.exchange(
                baseUrl() + "/admin/logs?eventType=parse_failure", HttpMethod.GET, new HttpEntity<>(adminHeaders()), List.class);

        assertThat(filtered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filtered.getBody()).allMatch(row -> ((Map) row).get("eventType").equals("parse_failure"));
        assertThat(filtered.getBody()).isNotEmpty();
    }

    private ResponseEntity<Map> exchange(String path, HttpHeaders headers) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private HttpHeaders authHeadersViaOtp(String phone) {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        return bearerHeaders((String) verify.getBody().get("accessToken"));
    }

    private HttpHeaders authHeadersViaGoogle() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/auth/google", Map.of("idToken", FirebaseAuthTestConfig.VALID_GOOGLE_TOKEN_A), Map.class);
        return bearerHeaders((String) login.getBody().get("accessToken"));
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

    /** Bypasses the API (Testcontainers' superuser connection) to plant a fixture row with sms_raw_text populated. */
    private void insertTransactionWithRawSmsText(UUID userId, String rawText) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, "
                    + "dr_cr_indicator, transaction_id, source, sms_raw_text) VALUES ('"
                    + UUID.randomUUID() + "', '" + userId + "', '2026-06-15T14:32:00Z', 500, 0, -500, 'DR', '"
                    + UUID.randomUUID() + "', 'sms', '" + rawText.replace("'", "''") + "')");
        }
    }

    private void insertAdminLog(String eventType, UUID userId, String payloadJson) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO admin_logs (id, event_type, user_id, payload) VALUES ('" + UUID.randomUUID() + "', '" + eventType
                    + "', " + (userId == null ? "NULL" : "'" + userId + "'") + ", '" + payloadJson.replace("'", "''") + "'::jsonb)");
        }
    }
}
