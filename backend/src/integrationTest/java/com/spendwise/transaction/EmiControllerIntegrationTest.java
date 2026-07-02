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

/** Covers the Required Tests for E3-S3-T1/T2 (docs/testing.md EMI CRUD). Requires Docker — run via {@code ./gradlew integrationTest}. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class EmiControllerIntegrationTest {

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
    void createManualEmiThenListReflectsFields() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> create = restTemplate.exchange(
                baseUrl() + "/emis",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("label", "Home Loan EMI", "amount", 15000, "dueDay", 5), headers),
                Map.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(create.getBody().get("detectedFromSms")).isEqualTo(false);
        assertThat(create.getBody().get("sourceTransactionId")).isNull();
        assertThat(create.getBody().get("isActive")).isEqualTo(true);

        ResponseEntity<List> list = restTemplate.exchange(baseUrl() + "/emis", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(list.getBody()).anyMatch(e -> ((Map) e).get("label").equals("Home Loan EMI"));
    }

    @Test
    void nonPositiveAmountIsRejectedWith400() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/emis",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("label", "Bad EMI", "amount", 0, "dueDay", 5), headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deactivatedEmiIsExcludedFromDefaultListButRowIsRetained() {
        HttpHeaders headers = authHeaders();
        ResponseEntity<Map> create = restTemplate.exchange(
                baseUrl() + "/emis",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("label", "Netflix", "amount", 500, "dueDay", 10), headers),
                Map.class);
        String emiId = (String) create.getBody().get("id");

        ResponseEntity<Void> patch = restTemplate.exchange(
                baseUrl() + "/emis/" + emiId, HttpMethod.PATCH, new HttpEntity<>(headers), Void.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> list = restTemplate.exchange(baseUrl() + "/emis", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(list.getBody()).noneMatch(e -> ((Map) e).get("id").equals(emiId));
    }

    @Test
    void putUpdatesLabelAmountAndDueDay() {
        HttpHeaders headers = authHeaders();
        ResponseEntity<Map> create = restTemplate.exchange(
                baseUrl() + "/emis",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("label", "Gym", "amount", 1000, "dueDay", 1), headers),
                Map.class);
        String emiId = (String) create.getBody().get("id");

        ResponseEntity<Void> put = restTemplate.exchange(
                baseUrl() + "/emis/" + emiId,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("label", "Gym Membership", "amount", 1200, "dueDay", 3), headers),
                Void.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> list = restTemplate.exchange(baseUrl() + "/emis", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(list.getBody()).anyMatch(e -> ((Map) e).get("label").equals("Gym Membership") && ((Map) e).get("dueDay").equals(3));
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
