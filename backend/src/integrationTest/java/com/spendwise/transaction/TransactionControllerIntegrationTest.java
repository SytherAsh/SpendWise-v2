package com.spendwise.transaction;

import com.spendwise.auth.FirebaseAuthTestConfig;
import org.junit.jupiter.api.Test;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E3-S2-T1 through T5 (docs/testing.md Transaction Management unit
 * tests + Pagination integration test) and the E3-S1-T3 black-box exclusion test deferred here
 * since it needs a GET endpoint to test against. Requires Docker — run via
 * {@code ./gradlew integrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class TransactionControllerIntegrationTest {

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
    void manuallyCreatedTransactionAppearsInListWithSourceManual() {
        HttpHeaders headers = authHeadersViaOtp();

        ResponseEntity<Map> create = restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "transactionDate", "2025-06-15T14:32:00Z",
                                "amount", -250.0,
                                "recipientName", "Corner Store"),
                        headers),
                Map.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create.getBody().get("source")).isEqualTo("manual");
        String createdId = (String) create.getBody().get("id");

        ResponseEntity<Map> list = restTemplate.exchange(
                baseUrl() + "/transactions", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) list.getBody().get("data");
        assertThat(data).anyMatch(t -> t.get("id").equals(createdId) && t.get("source").equals("manual"));
    }

    @Test
    void userACannotFetchUserBsTransactionById() {
        HttpHeaders userAHeaders = authHeadersViaOtp();
        HttpHeaders userBHeaders = authHeadersViaGoogle();

        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactionDate", "2025-06-15T14:32:00Z", "amount", -100.0), userBHeaders),
                Map.class);
        String userBTransactionId = (String) created.getBody().get("id");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/transactions/" + userBTransactionId, HttpMethod.GET, new HttpEntity<>(userAHeaders), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void smsRawTextIsNeverPresentInAnyTransactionResponse() throws Exception {
        HttpHeaders headers = authHeadersViaOtp();
        UUID userId = currentUserId(headers);
        String rawSecret = "URGENT: your OTP is 482913, do not share with anyone.";
        UUID fixtureId = insertTransactionWithRawSmsText(userId, rawSecret);

        ResponseEntity<String> getById = restTemplate.exchange(
                baseUrl() + "/transactions/" + fixtureId, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        ResponseEntity<String> list = restTemplate.exchange(
                baseUrl() + "/transactions", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        for (String body : List.of(getById.getBody(), list.getBody())) {
            assertThat(body).doesNotContain("sms_raw_text");
            assertThat(body).doesNotContain("smsRawText");
            assertThat(body).doesNotContain(rawSecret);
        }
    }

    @Test
    void categoryCorrectionUpdatesBothTablesAtomicallyAndHandlesErrorCases() {
        HttpHeaders headers = authHeadersViaOtp();
        ResponseEntity<Map> created = restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactionDate", "2025-06-15T14:32:00Z", "amount", -100.0), headers),
                Map.class);
        String transactionId = (String) created.getBody().get("id");

        ResponseEntity<Map> correction = restTemplate.exchange(
                baseUrl() + "/transactions/" + transactionId + "/category",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("category_id", 7), headers),
                Map.class);
        assertThat(correction.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> afterCorrection = restTemplate.exchange(
                baseUrl() + "/transactions/" + transactionId, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(afterCorrection.getBody().get("categoryId")).isEqualTo(7);
        assertThat(afterCorrection.getBody().get("assignedBy")).isEqualTo("user");

        ResponseEntity<Map> missingTransaction = restTemplate.exchange(
                baseUrl() + "/transactions/" + UUID.randomUUID() + "/category",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("category_id", 7), headers),
                Map.class);
        assertThat(missingTransaction.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> invalidCategory = restTemplate.exchange(
                baseUrl() + "/transactions/" + transactionId + "/category",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("category_id", 9999), headers),
                Map.class);
        assertThat(invalidCategory.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getCategoriesReturnsAllTenSeededCategories() {
        HttpHeaders headers = authHeadersViaOtp();

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/categories", HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(10);
    }

    @Test
    void cursorPaginationReturnsConsistentNonDuplicatedResultsAcrossPagesEvenWithConcurrentInserts() {
        HttpHeaders headers = authHeadersViaOtp();
        createManual(headers, "2025-01-01T00:00:00Z", -10.0);
        createManual(headers, "2025-01-02T00:00:00Z", -20.0);
        createManual(headers, "2025-01-03T00:00:00Z", -30.0);

        ResponseEntity<Map> firstPage = restTemplate.exchange(
                baseUrl() + "/transactions?limit=2", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        List<Map<String, Object>> firstPageData = (List<Map<String, Object>>) firstPage.getBody().get("data");
        assertThat(firstPageData).hasSize(2);
        assertThat((Boolean) firstPage.getBody().get("hasMore")).isTrue();
        String cursor = (String) firstPage.getBody().get("nextCursor");

        // Simulate a new transaction arriving between page fetches — newer than everything
        // already paged through, so it must not appear in (or shift) the already-issued cursor's remaining page.
        createManual(headers, "2025-01-04T00:00:00Z", -40.0);

        ResponseEntity<Map> secondPage = restTemplate.exchange(
                baseUrl() + "/transactions?limit=2&cursor=" + cursor, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        List<Map<String, Object>> secondPageData = (List<Map<String, Object>>) secondPage.getBody().get("data");

        List<Object> firstPageIds = firstPageData.stream().map(t -> t.get("id")).toList();
        List<Object> secondPageIds = secondPageData.stream().map(t -> t.get("id")).toList();
        assertThat(secondPageIds).doesNotContainAnyElementsOf(firstPageIds);
        assertThat(secondPageData).hasSize(1); // the original 3rd (oldest) transaction only
        assertThat((Boolean) secondPage.getBody().get("hasMore")).isFalse();
    }

    private void createManual(HttpHeaders headers, String transactionDate, double amount) {
        restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactionDate", transactionDate, "amount", amount), headers),
                Map.class);
    }

    private HttpHeaders authHeadersViaOtp() {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", "+919100000088", "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        return bearerHeaders((String) verify.getBody().get("accessToken"));
    }

    private HttpHeaders authHeadersViaGoogle() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/auth/google", Map.of("idToken", FirebaseAuthTestConfig.VALID_GOOGLE_TOKEN_A), Map.class);
        return bearerHeaders((String) login.getBody().get("accessToken"));
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

    /** Bypasses the API (RLS-bypassing superuser connection) to plant a fixture row with sms_raw_text populated. */
    private UUID insertTransactionWithRawSmsText(UUID userId, String rawText) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection =
                        DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, "
                    + "dr_cr_indicator, transaction_id, source, sms_raw_text) VALUES ('"
                    + id + "', '" + userId + "', '2025-06-15T14:32:00Z', 350, 0, -350, 'DR', 'txn_rawtext_fixture', "
                    + "'sms', '" + rawText.replace("'", "''") + "')");
        }
        return id;
    }
}
