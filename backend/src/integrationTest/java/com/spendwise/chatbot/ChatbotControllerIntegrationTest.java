package com.spendwise.chatbot;

import com.spendwise.auth.FirebaseAuthTestConfig;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E8-S3-T1/T2 (docs/api.md `/chatbot`). Requires Docker — run via
 * {@code ./gradlew integrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class ChatbotControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void newSessionAppearsFirstInTheList() {
        Session session = loginPhone("+919100000099");

        ResponseEntity<Map> first = createSession(session);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        String firstId = (String) first.getBody().get("id");
        ResponseEntity<Map> second = createSession(session);
        String secondId = (String) second.getBody().get("id");

        ResponseEntity<List> list = restTemplate.exchange(
                baseUrl() + "/chatbot/sessions", HttpMethod.GET, new HttpEntity<>(session.headers()), List.class);
        List<Map<String, Object>> body = list.getBody();
        assertThat(body.get(0).get("id")).isEqualTo(secondId);
        assertThat(body).extracting(row -> row.get("id")).contains(firstId);
    }

    @Test
    void userBCannotFetchUserAsSessionHistory() {
        Session userA = loginPhone("+919100000100");
        ResponseEntity<Map> created = createSession(userA);
        String sessionId = (String) created.getBody().get("id");
        Session userB = loginGoogle();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/chatbot/sessions/" + sessionId, HttpMethod.GET, new HttpEntity<>(userB.headers()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sendingAMessagePersistsBothRolesInChronologicalOrderAndSurvivesRefetch() throws Exception {
        Session session = loginPhone("+919100000101");
        insertCategorizedTransaction(session.userId(), 7, "3240.00");
        ResponseEntity<Map> created = createSession(session);
        String sessionId = (String) created.getBody().get("id");

        ResponseEntity<Map> messageResponse = restTemplate.exchange(
                baseUrl() + "/chatbot/message",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", sessionId, "message", "How much did I spend on food?"), session.headers()),
                Map.class);
        assertThat(messageResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(messageResponse.getBody().get("role")).isEqualTo("assistant");

        ResponseEntity<Map> history = restTemplate.exchange(
                baseUrl() + "/chatbot/sessions/" + sessionId, HttpMethod.GET, new HttpEntity<>(session.headers()), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) history.getBody().get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role")).isEqualTo("user");
        assertThat(messages.get(0).get("message")).isEqualTo("How much did I spend on food?");
        assertThat(messages.get(1).get("role")).isEqualTo("assistant");
    }

    private ResponseEntity<Map> createSession(Session session) {
        return restTemplate.postForEntity(baseUrl() + "/chatbot/sessions", new HttpEntity<>(session.headers()), Map.class);
    }

    private void insertCategorizedTransaction(UUID userId, int categoryId, String amount) throws Exception {
        UUID transactionId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, "
                    + "transaction_mode, dr_cr_indicator, transaction_id, recipient_name, bank, upi_id, note, source, parsed_at) "
                    + "VALUES ('" + transactionId + "', '" + userId + "', NOW(), " + amount + ", 0, -" + amount + ", "
                    + "'UPI', 'DR', '" + UUID.randomUUID() + "', 'Swiggy', 'ICICI', 'swiggy@upi', NULL, 'sms', NOW())");
            statement.execute("INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by) "
                    + "VALUES ('" + transactionId + "', " + categoryId + ", 0.9, 'ml')");
        }
    }

    private Session loginPhone(String phone) {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        return toSession(verify);
    }

    private Session loginGoogle() {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/google", Map.of("idToken", FirebaseAuthTestConfig.VALID_GOOGLE_TOKEN_A), Map.class);
        return toSession(verify);
    }

    private Session toSession(ResponseEntity<Map> response) {
        String accessToken = (String) response.getBody().get("accessToken");
        String userId = (String) ((Map<?, ?>) response.getBody().get("user")).get("id");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new Session(UUID.fromString(userId), headers);
    }

    private record Session(UUID userId, HttpHeaders headers) {}
}
