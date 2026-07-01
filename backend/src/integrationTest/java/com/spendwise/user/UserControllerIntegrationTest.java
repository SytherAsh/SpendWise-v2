package com.spendwise.user;

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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the Required Tests for E1-S3-T1/T2/T3 (docs/testing.md). Requires Docker (Testcontainers). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class UserControllerIntegrationTest {

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

    private HttpHeaders authHeadersFor(String phone) {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        String accessToken = (String) verify.getBody().get("accessToken");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void updateProfileThenGetReflectsTheChange() {
        HttpHeaders headers = authHeadersFor("+919100000001");

        ResponseEntity<Map> putResponse = restTemplate.exchange(
                baseUrl() + "/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("email", "updated@example.com"), headers),
                Map.class);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                baseUrl() + "/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(getResponse.getBody().get("email")).isEqualTo("updated@example.com");
    }

    @Test
    void putPreferencesThenGetReflectsIt() {
        HttpHeaders headers = authHeadersFor("+919100000002");

        ResponseEntity<Map> putResponse = restTemplate.exchange(
                baseUrl() + "/users/me/preferences",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("alertChannels", Map.of("push", false, "email", true)), headers),
                Map.class);
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                baseUrl() + "/users/me/preferences", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(getResponse.getBody().get("alertChannels")).isEqualTo(Map.of("push", false, "email", true));
    }

    @Test
    void onboardingResponseContainsRawKeyAndOnlyHashIsPersisted() throws Exception {
        HttpHeaders headers = authHeadersFor("+919100000003");

        ResponseEntity<Map> onboardingResponse = restTemplate.exchange(
                baseUrl() + "/users/me/onboarding",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "consentText", "I consent to SMS access, storage, and ML training",
                                "appVersion", "1.0.0",
                                "selectedApps", java.util.List.of("paytm"),
                                "selectedBanks", java.util.List.of("SBI")),
                        headers),
                Map.class);
        assertThat(onboardingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rawKey = (String) onboardingResponse.getBody().get("deviceApiKey");
        assertThat(rawKey).isNotBlank();

        // Bypass RLS via the container's superuser to verify what's actually persisted.
        try (Connection connection =
                        DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            try (ResultSet countAll = statement.executeQuery("SELECT count(*) FROM device_api_keys")) {
                countAll.next();
                assertThat(countAll.getInt(1)).isGreaterThanOrEqualTo(1);
            }
            try (ResultSet rawKeyLookup =
                    statement.executeQuery("SELECT count(*) FROM device_api_keys WHERE key_hash = '" + rawKey + "'")) {
                rawKeyLookup.next();
                assertThat(rawKeyLookup.getInt(1)).isZero(); // the raw key is never stored, only its hash
            }
        }
    }
}
