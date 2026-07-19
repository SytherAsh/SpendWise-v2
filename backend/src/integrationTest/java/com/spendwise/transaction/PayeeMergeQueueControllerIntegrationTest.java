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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Merge Payees review-queue endpoint (Merge Payees feature, ML strategy phase). Requires Docker —
 * run via {@code ./gradlew integrationTest}.
 *
 * <p>Regression guard for the empty-body-403 bug: {@code getMergeQueueSnapshot} reads {@code
 * recipient_merge_suggestions} through the RLS-scoped datasource, so it must run inside {@code
 * @Transactional} for {@code set_config('app.current_user_id', …, is_local := true)} and the SELECT
 * to share one connection. Without it, the GUC is empty on the pooled connection and the RLS
 * policy's {@code current_setting(…, true)::uuid} throws {@code invalid input syntax for type uuid:
 * ""} — which, thrown from the controller, forwards to {@code /error}, where {@code
 * UserJwtAuthFilter} (an {@code OncePerRequestFilter}, skipped on the ERROR dispatch) leaves no
 * SecurityContext and the request is denied with a bare 403. This asserts the GET returns 200 with
 * an empty queue instead.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class PayeeMergeQueueControllerIntegrationTest {

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
    void queueReturnsEmptyOkForUserWithNoSuggestions() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/payee-merge-queue", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("nextGroup")).isNull();
        assertThat(response.getBody().get("remainingGroupCount")).isEqualTo(0);
    }

    @Test
    void resolveWithNoDecisionsIsAcceptedAndReportsZeroCounts() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/payee-merge-queue/resolve",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("decisions", List.of()), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("confirmedSame")).isEqualTo(0);
        assertThat(response.getBody().get("confirmedDifferent")).isEqualTo(0);
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
