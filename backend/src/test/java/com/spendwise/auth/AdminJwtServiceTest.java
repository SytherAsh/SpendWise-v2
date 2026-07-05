package com.spendwise.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminJwtServiceTest {

    private static final String SECRET = "unit-test-admin-jwt-secret-value-1234567890";

    private final AdminJwtService service = new AdminJwtService(SECRET);

    @Test
    void issuedTokenHasTwentyFourHourExpiryClaimAndCarriesTheUsernameAsSubject() {
        String token = service.issueAccessToken("admin");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        long expiresInSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(expiresInSeconds).isEqualTo(AdminJwtService.ACCESS_TOKEN_TTL_SECONDS);
        assertThat(AdminJwtService.ACCESS_TOKEN_TTL_SECONDS).isEqualTo(86_400);
        assertThat(claims.getSubject()).isEqualTo("admin");
    }
}
