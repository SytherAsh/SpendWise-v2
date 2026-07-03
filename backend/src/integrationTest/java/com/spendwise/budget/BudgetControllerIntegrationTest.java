package com.spendwise.budget;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E5-S1 (docs/testing.md Budget unit/integration tests). Requires
 * Docker — run via {@code ./gradlew integrationTest}.
 *
 * <p>{@link FirebaseAuthTestConfig#VALID_OTP_TOKEN_PHONE_A} always resolves to the same fixed
 * identity regardless of the phone number in the request body (see {@code FirebaseAuthTestConfig}
 * — the token, not the request, determines identity), so every test method below shares one real
 * user's data for the lifetime of this class's single Testcontainers instance. Each test uses its
 * own category id and asserts with {@code filteredOn}/{@code anySatisfy} rather than exact list
 * sizes, the same accommodation {@code EmiControllerIntegrationTest} makes for the same reason.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class BudgetControllerIntegrationTest {

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
    void repeatedUpsertWithIdenticalParamsIsIdempotent() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> first = upsert(headers, 1, 2000);
        ResponseEntity<Map> second = upsert(headers, 1, 2000);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("id")).isEqualTo(first.getBody().get("id"));

        ResponseEntity<List> list = restTemplate.exchange(baseUrl() + "/budgets", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(list.getBody()).filteredOn(row -> ((Map) row).get("categoryId").equals(1)).hasSize(1);
    }

    @Test
    void nonPositiveLimitIsRejectedWith400() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> response = upsert(headers, 2, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownCategoryIsRejectedWith400() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> response = upsert(headers, 9999, 2000);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void suggestionsAreGracefullyUnavailableWithNoTransactionHistory() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<List> response =
                restTemplate.exchange(baseUrl() + "/budgets/suggestions", HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // No transaction has been ingested for this user anywhere in this test class, so every
        // category is still "no suggestion available" (E5-S1-T4 DoD).
        assertThat(response.getBody()).allMatch(row -> ((Map) row).get("available").equals(false));
    }

    @Test
    void progressReflectsZeroSpendForACategoryWithNoTransactionsYet() {
        HttpHeaders headers = authHeaders();
        upsert(headers, 3, 1000);

        ResponseEntity<List> response =
                restTemplate.exchange(baseUrl() + "/budgets/progress", HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertThat(response.getBody()).filteredOn(row -> ((Map) row).get("categoryId").equals(3)).anySatisfy(row -> {
            Map<?, ?> progress = (Map<?, ?>) row;
            assertThat(new BigDecimal(progress.get("spent").toString())).isEqualByComparingTo("0");
            assertThat(new BigDecimal(progress.get("percentSpent").toString())).isEqualByComparingTo("0.00");
        });
    }

    private ResponseEntity<Map> upsert(HttpHeaders headers, int categoryId, int monthlyLimit) {
        return restTemplate.exchange(
                baseUrl() + "/budgets",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("categoryId", categoryId, "monthlyLimit", monthlyLimit), headers),
                Map.class);
    }

    private HttpHeaders authHeaders() {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", "+911111111111", "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) verify.getBody().get("accessToken"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
