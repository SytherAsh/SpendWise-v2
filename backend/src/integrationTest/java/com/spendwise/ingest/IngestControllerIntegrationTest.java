package com.spendwise.ingest;

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
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E3-S1-T1 (dual-auth guard — the "4+1" cases from docs/testing.md
 * Ingest dual-auth validation) and E3-S1-T2 (batch persistence with two-layer dedup). Requires
 * Docker for the Testcontainers Postgres instance — run via {@code ./gradlew integrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class IngestControllerIntegrationTest {

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

    private record Session(String accessToken, String deviceApiKey) {}

    @Test
    void validJwtAndValidDeviceKeyProceedsAndPersists() {
        Session session = onboardViaOtp();

        ResponseEntity<Map> response = postBatch(session, List.of(item("txn_valid_1", "swiggy@okicici", -350.0)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("status")).isEqualTo(201);
        assertThat(countTransactionsWithId("txn_valid_1")).isEqualTo(1);
    }

    @Test
    void missingJwtReturns401() {
        Session session = onboardViaOtp();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Key", session.deviceApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/ingest/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactions", List.of(item("txn_no_jwt", null, -1.0))), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingDeviceKeyReturns401() {
        Session session = onboardViaOtp();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/ingest/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactions", List.of(item("txn_no_key", null, -1.0))), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deviceKeyNotMatchingUserIdReturns401() {
        Session userA = onboardViaOtp();
        Session userB = onboardViaGoogle();
        // userB's JWT paired with userA's device key — mismatched pair.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userB.accessToken());
        headers.set("X-Device-Key", userA.deviceApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/ingest/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactions", List.of(item("txn_mismatch", null, -1.0))), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void inactiveDeviceKeyReturns401() throws Exception {
        Session session = onboardViaOtp();
        String inactiveRawKey = "inactive-test-key-" + UUID.randomUUID();
        insertInactiveDeviceKeyDirectly(session.accessToken(), inactiveRawKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        headers.set("X-Device-Key", inactiveRawKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/ingest/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactions", List.of(item("txn_inactive_key", null, -1.0))), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void postingSameTransactionTwiceReturns409SecondTimeWithNoDuplicateRow() {
        Session session = onboardViaOtp();
        Map<String, Object> transaction = item("txn_dedup_primary", null, -500.0);

        ResponseEntity<Map> first = postBatch(session, List.of(transaction));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> second = postBatch(session, List.of(transaction));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        List<Map<String, Object>> results = (List<Map<String, Object>>) second.getBody().get("results");
        assertThat(results.get(0).get("status")).isEqualTo(409);
        assertThat(countTransactionsWithId("txn_dedup_primary")).isEqualTo(1);
    }

    @Test
    void mixedBatchWithOneDuplicatePersistsTheOtherTwoAndReportsCorrectPerItemStatuses() {
        Session session = onboardViaOtp();
        postBatch(session, List.of(item("txn_seed", null, -100.0)));

        ResponseEntity<Map> response = postBatch(
                session,
                List.of(
                        item("txn_mixed_a", null, -10.0),
                        item("txn_seed", null, -100.0), // duplicate of the seeded transaction
                        item("txn_mixed_b", null, -20.0)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); // not all items were duplicates
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.getBody().get("results");
        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("status")).isEqualTo(201);
        assertThat(results.get(1).get("status")).isEqualTo(409);
        assertThat(results.get(2).get("status")).isEqualTo(201);
        assertThat(countTransactionsWithId("txn_mixed_a")).isEqualTo(1);
        assertThat(countTransactionsWithId("txn_seed")).isEqualTo(1);
        assertThat(countTransactionsWithId("txn_mixed_b")).isEqualTo(1);
    }

    private ResponseEntity<Map> postBatch(Session session, List<Map<String, Object>> transactions) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        headers.set("X-Device-Key", session.deviceApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl() + "/ingest/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactions", transactions), headers),
                Map.class);
    }

    private static Map<String, Object> item(String transactionId, String upiId, double amount) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("transaction_date", "2025-06-15T14:32:00Z");
        map.put("debit", amount < 0 ? -amount : 0);
        map.put("credit", amount < 0 ? 0 : amount);
        map.put("amount", amount);
        map.put("dr_cr_indicator", amount < 0 ? "DR" : "CR");
        map.put("transaction_id", transactionId);
        map.put("recipient_name", "Swiggy");
        map.put("upi_id", upiId);
        map.put("bank", "ICICI");
        map.put("transaction_mode", "UPI");
        map.put("note", null);
        map.put("source", "sms");
        return map;
    }

    private Session onboardViaOtp() {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", "+919100000099", "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        return onboard((String) verify.getBody().get("accessToken"));
    }

    private Session onboardViaGoogle() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl() + "/auth/google", Map.of("idToken", FirebaseAuthTestConfig.VALID_GOOGLE_TOKEN_A), Map.class);
        return onboard((String) login.getBody().get("accessToken"));
    }

    private Session onboard(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> onboarding = restTemplate.exchange(
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
        return new Session(accessToken, (String) onboarding.getBody().get("deviceApiKey"));
    }

    /** Bypasses the API to insert an inactive device key row directly, mirroring UserControllerIntegrationTest's RLS-bypass pattern. */
    private void insertInactiveDeviceKeyDirectly(String accessToken, String rawKey) throws Exception {
        String userId = unverifiedSubject(accessToken);
        String keyHash = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        try (Connection connection =
                        DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO device_api_keys (id, user_id, key_hash, is_active) VALUES ('"
                    + UUID.randomUUID() + "', '" + userId + "', '" + keyHash + "', FALSE)");
        }
    }

    /**
     * Test-only: reads the JWT's {@code sub} claim without verifying the signature (the signing
     * secret isn't wired into this test) — safe because it's our own just-issued test token.
     */
    private static String unverifiedSubject(String jwt) {
        String[] parts = jwt.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return payloadJson.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
    }

    private int countTransactionsWithId(String transactionId) {
        try (Connection connection =
                        DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT count(*) FROM transactions WHERE transaction_id = '" + transactionId + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
