package com.spendwise.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and validates the user session JWT, signed with {@code JWT_SECRET} (E1-S1-T2).
 * Deliberately independent of {@link AdminJwtAuthFilter}'s validation logic beyond the JJWT
 * library itself — see E1-S2-T1's isolation requirement.
 */
@Service
public class UserJwtService {

    public static final long ACCESS_TOKEN_TTL_SECONDS = 604_800; // 7 days, per docs/api.md

    private final SecretKey signingKey;

    public UserJwtService(@Value("${app.security.jwt-secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL_SECONDS, ChronoUnit.SECONDS)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @throws InvalidUserJwtException if the token is missing, malformed, expired, or not
     *     signed with {@code JWT_SECRET} (including tokens signed with {@code ADMIN_JWT_SECRET})
     */
    public UUID validateAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidUserJwtException("Invalid or expired access token", e);
        }
    }
}
