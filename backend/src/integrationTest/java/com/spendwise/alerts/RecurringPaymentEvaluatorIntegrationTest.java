package com.spendwise.alerts;

import com.spendwise.auth.FirebaseAuthTestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Required test for E6-S2-T1 (docs/testing.md Alerts unit tests — recurring-payment detection;
 * epic DoD "run the job again and confirm no duplicate alert is created for the same
 * still-unconfirmed group"). Invokes {@link AlertEvaluatorJob}'s package-private {@code run()}
 * directly, mirroring {@code CategorizationJobsIntegrationTest} and {@code
 * AlertControllerIntegrationTest}'s "autowire the collaborator directly" approach. Requires
 * Docker — run via {@code ./gradlew integrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class RecurringPaymentEvaluatorIntegrationTest {

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

    @Autowired
    private AlertEvaluatorJob alertEvaluatorJob;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void qualifyingGroupProducesExactlyOneAlertAndRerunningDoesNotDuplicateIt() throws Exception {
        UUID userId = onboardUser("+919100000200");
        insertTransactionDirectly(userId, "netflix@okicici", "Netflix", "199.00", 50);
        insertTransactionDirectly(userId, "netflix@okicici", "Netflix", "199.00", 25);
        insertTransactionDirectly(userId, "netflix@okicici", "Netflix", "205.00", 0);

        alertEvaluatorJob.run();

        List<Map<String, Object>> firstRun = fetchRecurringPaymentAlerts(userId);
        assertThat(firstRun).hasSize(1);
        Map<String, Object> payload = firstRun.get(0);
        assertThat(payload.get("merchant_key")).isEqualTo("netflix@okicici");
        assertThat(payload.get("merchant_label")).isEqualTo("Netflix");

        alertEvaluatorJob.run();

        assertThat(fetchRecurringPaymentAlerts(userId)).hasSize(1);
    }

    private UUID onboardUser(String phone) {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify", Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A), Map.class);
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
        return UUID.fromString((String) ((Map<?, ?>) verify.getBody().get("user")).get("id"));
    }

    private void insertTransactionDirectly(UUID userId, String upiId, String recipientName, String amount, int daysAgo) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, "
                    + "transaction_mode, dr_cr_indicator, transaction_id, recipient_name, bank, upi_id, note, source, parsed_at) "
                    + "VALUES ('" + UUID.randomUUID() + "', '" + userId + "', NOW() - INTERVAL '" + daysAgo + " days', " + amount
                    + ", 0, -" + amount + ", 'UPI', 'DR', '" + UUID.randomUUID() + "', '" + recipientName + "', 'ICICI', '" + upiId
                    + "', NULL, 'sms', NOW())");
        }
    }

    private List<Map<String, Object>> fetchRecurringPaymentAlerts(UUID userId) throws Exception {
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT payload FROM alerts WHERE user_id = '" + userId + "' AND type = 'recurring_payment'")) {
            while (rs.next()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                results.add(mapper.readValue(rs.getString("payload"), Map.class));
            }
        }
        return results;
    }
}
