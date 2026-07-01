package com.spendwise.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E1-S1-T3 through E1-S1-T7 and E1-S2-T1's integration-level
 * assertion (docs/testing.md). Requires Docker for the Testcontainers Postgres instance — run
 * via {@code ./gradlew integrationTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    @Value("${app.security.admin-jwt-secret}")
    private String adminJwtSecret;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void otpSendThenVerifyHappyPathReturnsMatchingSchema() {
        String phone = "+919000000001";

        ResponseEntity<Void> sendResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/send", Map.of("phone", phone), Void.class);
        assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> verifyResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = verifyResponse.getBody();
        assertThat(body.keySet()).contains("accessToken", "refreshToken", "expiresIn", "user");
        assertThat(((Number) body.get("expiresIn")).longValue()).isEqualTo(604_800L);
        Map<?, ?> user = (Map<?, ?>) body.get("user");
        assertThat(user.get("phone")).isEqualTo("+911111111111"); // resolved from the verified token, not the request body
        assertThat(user.get("email")).isNull();
    }

    @Test
    void sixthOtpSendWithinAnHourIsRateLimited() {
        String phone = "+919000000002";
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    baseUrl() + "/auth/otp/send", Map.of("phone", phone), Void.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<Map> sixthResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/send", Map.of("phone", phone), Map.class);

        assertThat(sixthResponse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void otpVerifyWithInvalidTokenReturns400() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", "+919000000003", "idToken", "not-a-real-token"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void googleLoginFirstTimeCreatesUserSecondTimeReusesSameId() {
        ResponseEntity<Map> first = restTemplate.postForEntity(
                baseUrl() + "/auth/google",
                Map.of("idToken", FirebaseAuthTestConfig.VALID_GOOGLE_TOKEN_A),
                Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        String firstUserId = (String) ((Map<?, ?>) first.getBody().get("user")).get("id");

        ResponseEntity<Map> second = restTemplate.postForEntity(
                baseUrl() + "/auth/google",
                Map.of("idToken", FirebaseAuthTestConfig.VALID_GOOGLE_TOKEN_A),
                Map.class);
        String secondUserId = (String) ((Map<?, ?>) second.getBody().get("user")).get("id");

        assertThat(secondUserId).isEqualTo(firstUserId);
    }

    @Test
    void normalRotationSucceedsAndOldTokenStopsWorking() {
        String refreshToken = obtainRefreshToken("+919000000004");

        ResponseEntity<Map> rotateResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/token/refresh", Map.of("refreshToken", refreshToken), Map.class);
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefreshToken = (String) rotateResponse.getBody().get("refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        ResponseEntity<Map> reuseOldResponse = restTemplate.postForEntity(
                baseUrl() + "/auth/token/refresh", Map.of("refreshToken", refreshToken), Map.class);
        assertThat(reuseOldResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void replayingAnAlreadyRotatedTokenRevokesAllSessionsForThatUser() {
        String phone = "+919000000005";
        String sessionOneRefreshToken = obtainRefreshToken(phone);
        // A second, independent session for the same user (e.g. a second device).
        ResponseEntity<Map> secondLogin = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        String sessionTwoRefreshToken = (String) secondLogin.getBody().get("refreshToken");

        // Rotate session one once (now valid/current)...
        ResponseEntity<Map> rotated = restTemplate.postForEntity(
                baseUrl() + "/auth/token/refresh", Map.of("refreshToken", sessionOneRefreshToken), Map.class);
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);

        // ...then replay the now-stale original token.
        ResponseEntity<Map> replay = restTemplate.postForEntity(
                baseUrl() + "/auth/token/refresh", Map.of("refreshToken", sessionOneRefreshToken), Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Session two — an entirely different, still-unused valid token — must also be dead now.
        ResponseEntity<Map> sessionTwoAfterReplay = restTemplate.postForEntity(
                baseUrl() + "/auth/token/refresh", Map.of("refreshToken", sessionTwoRefreshToken), Map.class);
        assertThat(sessionTwoAfterReplay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutThenRefreshWithSameTokenReturns401() {
        String phone = "+919000000006";
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        String accessToken = (String) verify.getBody().get("accessToken");
        String refreshToken = (String) verify.getBody().get("refreshToken");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> logoutRequest = new HttpEntity<>(Map.of("refreshToken", refreshToken), headers);
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                baseUrl() + "/auth/logout", HttpMethod.POST, logoutRequest, Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> refreshAfterLogout = restTemplate.postForEntity(
                baseUrl() + "/auth/token/refresh", Map.of("refreshToken", refreshToken), Map.class);
        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminRouteRejectsAJwtSecretSignedTokenEvenWithAnAdminRoleClaim() {
        String userSignedTokenWithAdminClaim = signedToken(jwtSecret, UUID.randomUUID().toString(), Map.of("role", "admin"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userSignedTokenWithAdminClaim);
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/admin/anything", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userRouteRejectsAnAdminSecretSignedToken() {
        String adminSignedToken = signedToken(adminJwtSecret, "admin-1", Map.of());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminSignedToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("refreshToken", "irrelevant"), headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/auth/logout", HttpMethod.POST, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String obtainRefreshToken(String phone) {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", phone, "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        return (String) verify.getBody().get("refreshToken");
    }

    private static String signedToken(String secret, String subject, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(extraClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }
}
