package com.spendwise.categorization;

import com.spendwise.auth.FirebaseAuthTestConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required tests for E4-S3-T3 (categorization retry job) and E4-S3-T4 (ML retraining job),
 * invoking each job's package-private {@code run()} directly rather than waiting on the real
 * schedule. Requires Docker for the Testcontainers Postgres instance — run via {@code ./gradlew
 * integrationTest}. Uses an embedded {@link HttpServer} stub in place of the real FastAPI service
 * (docs/testing.md allows "a test double" for E4-S3-T4's Required Test) so this test needs
 * neither Python nor a running ML service — only {@code /predict} and {@code /retrain} are
 * exercised, matching the epic's "test double or a real call against E4-S2-T4" wording.
 *
 * <p>The {@code spendwise_jobs} role (db-init/02-jobs-role.sql in real deployments) doesn't exist
 * in a bare Testcontainers Postgres image, so {@link #createJobsRole()} creates it directly,
 * mirroring {@code AbstractSchemaIntegrationTest}'s existing {@code spendwise_app} bootstrap
 * pattern for the same reason.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class CategorizationJobsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final AtomicInteger RETRAIN_CALL_COUNT = new AtomicInteger();
    private static final HttpServer STUB_ML_SERVICE;

    static {
        try {
            STUB_ML_SERVICE = HttpServer.create(new InetSocketAddress(0), 0);
            STUB_ML_SERVICE.createContext(
                    "/predict",
                    exchange -> respondJson(exchange, "{\"category_id\":7,\"category_name\":\"Food / Dine Out\",\"confidence\":0.94}"));
            STUB_ML_SERVICE.createContext(
                    "/retrain",
                    exchange -> {
                        RETRAIN_CALL_COUNT.incrementAndGet();
                        respondJson(exchange, "{\"status\":\"success\",\"trained_samples\":1}");
                    });
            STUB_ML_SERVICE.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void mlServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ml.base-url", () -> "http://localhost:" + STUB_ML_SERVICE.getAddress().getPort());
        registry.add("app.ml.internal-key", () -> "integration-test-key");
    }

    @BeforeAll
    static void createJobsRole() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE spendwise_jobs WITH LOGIN PASSWORD 'spendwise_jobs_password' BYPASSRLS");
            // Inherits privileges on tables Flyway creates later too (owned by the bootstrap
            // role in this test flavor — see AbstractSchemaIntegrationTest's javadoc).
            statement.execute("GRANT " + POSTGRES.getUsername() + " TO spendwise_jobs");
        }
    }

    @AfterAll
    static void stopStubMlService() {
        STUB_ML_SERVICE.stop(0);
    }

    private static void respondJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Autowired
    private CategorizationRetryJob categorizationRetryJob;

    @Autowired
    private MlRetrainingJob mlRetrainingJob;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void retryJobCategorizesAnUncategorizedTransaction() throws Exception {
        UUID userId = onboardUser();
        UUID transactionId = insertTransactionDirectly(userId, "txn_retry_test");

        categorizationRetryJob.run();

        assertThat(fetchAssignedCategory(transactionId)).contains(7);
    }

    @Test
    void retrainJobCallsFastApiRetrainEndpoint() throws Exception {
        UUID userId = onboardUser();
        UUID transactionId = insertTransactionDirectly(userId, "txn_retrain_test");
        insertCorrectionDirectly(transactionId);
        int callsBefore = RETRAIN_CALL_COUNT.get();

        mlRetrainingJob.run();

        assertThat(RETRAIN_CALL_COUNT.get()).isEqualTo(callsBefore + 1);
    }

    private UUID onboardUser() {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", "+919100000098", "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        String accessToken = (String) verify.getBody().get("accessToken");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(
                baseUrl() + "/users/me/onboarding",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "consentText", "I consent to SMS access, storage, and ML training",
                                "appVersion", "1.0.0",
                                "selectedApps", List.of("paytm"),
                                "selectedBanks", List.of("SBI")),
                        headers),
                Map.class);
        return UUID.fromString(unverifiedSubject(accessToken));
    }

    private UUID insertTransactionDirectly(UUID userId, String businessTransactionId) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, "
                    + "transaction_mode, dr_cr_indicator, transaction_id, recipient_name, bank, upi_id, note, source, parsed_at) "
                    + "VALUES ('" + id + "', '" + userId + "', NOW(), 350.0, 0, -350.0, 'UPI', 'DR', '" + businessTransactionId
                    + "', 'Swiggy', 'ICICI', 'swiggy@okicici', NULL, 'sms', NOW())");
        }
        return id;
    }

    private void insertCorrectionDirectly(UUID transactionId) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO ml_corrections (id, transaction_id, old_category_id, new_category_id, corrected_at) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + transactionId + "', NULL, 7, NOW())");
        }
    }

    private java.util.Optional<Integer> fetchAssignedCategory(UUID transactionId) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT category_id FROM transaction_categories WHERE transaction_id = '" + transactionId + "'")) {
            if (!rs.next()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(rs.getInt("category_id"));
        }
    }

    private static String unverifiedSubject(String jwt) {
        String[] parts = jwt.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return payloadJson.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
    }
}
