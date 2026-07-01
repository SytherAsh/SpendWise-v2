package com.spendwise.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserJwtServiceTest {

    private final UserJwtService service = new UserJwtService("unit-test-jwt-secret-value-1234567890");

    @Test
    void issuedTokenHasSevenDayExpiryClaim() {
        UUID userId = UUID.randomUUID();
        String token = service.issueAccessToken(userId);

        Claims claims = Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "unit-test-jwt-secret-value-1234567890".getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long expiresInSeconds =
                (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(expiresInSeconds).isEqualTo(UserJwtService.ACCESS_TOKEN_TTL_SECONDS);
        assertThat(UserJwtService.ACCESS_TOKEN_TTL_SECONDS).isEqualTo(604_800);
    }

    @Test
    void issuedTokenRoundTripsToSameUserId() {
        UUID userId = UUID.randomUUID();
        String token = service.issueAccessToken(userId);

        assertThat(service.validateAndGetUserId(token)).isEqualTo(userId);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        UserJwtService otherService = new UserJwtService("a-completely-different-secret-value-987654");
        String token = otherService.issueAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> service.validateAndGetUserId(token))
                .isInstanceOf(InvalidUserJwtException.class);
    }
}
