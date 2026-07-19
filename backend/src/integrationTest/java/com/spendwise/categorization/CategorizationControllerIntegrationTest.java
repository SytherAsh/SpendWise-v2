package com.spendwise.categorization;

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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code POST /categorization/recategorize} (ADR-020, ML strategy phase, 2026-07-20) — the first
 * user-facing endpoint on the Categorization module. Requires Docker — run via
 * {@code ./gradlew integrationTest}. Same auth/RLS-scoping pattern as {@code
 * PayeeMergeQueueControllerIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class CategorizationControllerIntegrationTest {

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
    void recategorizeReturnsZeroForAnIdentityWithNoTransactions() {
        HttpHeaders headers = authHeaders();
        // Map.of(...) rejects null values outright (throws NPE at construction) — recipient_name
        // is nullable per the actual request contract, so a mutable map is needed to express it.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipient_name", "Nobody");
        body.put("upi_id", null);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/categorization/recategorize", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("recategorized")).isEqualTo(0);
    }

    @Test
    void recategorizeRejectsUnauthenticatedRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/categorization/recategorize",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("recipient_name", "Nobody"), headers),
                Map.class);

        // Unlike /ingest/transactions (its own separate IngestSecurityConfig filter chain, which
        // does return 401 for a missing JWT), this route is guarded by SecurityConfig's default
        // filter chain, which has no custom AuthenticationEntryPoint — an unauthenticated request
        // denied by `.anyRequest().authenticated()` gets Spring Security's default 403, not 401.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders authHeaders() {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", "+919100000077", "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) verify.getBody().get("accessToken"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
