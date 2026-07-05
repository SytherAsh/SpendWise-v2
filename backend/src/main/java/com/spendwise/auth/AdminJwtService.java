package com.spendwise.auth;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Issues the admin session JWT, signed with {@code ADMIN_JWT_SECRET} — the issuer counterpart to
 * {@link AdminJwtAuthFilter}'s validator (E1-S2-T1). Deliberately parses the secret independently
 * of the filter (no shared key-derivation code) — same isolation reasoning as {@link
 * UserJwtService} vs. {@link AdminJwtAuthFilter}. Consumed only by Admin's own login endpoint
 * (Epic 11); never by any user-facing flow.
 */
@Service
public class AdminJwtService {

    /** Admin sessions are operator-driven, not end-user, so a shorter-lived token than the 7-day user access token is appropriate. */
    public static final long ACCESS_TOKEN_TTL_SECONDS = 86_400; // 24 hours

    private final SecretKey signingKey;

    public AdminJwtService(@Value("${app.security.admin-jwt-secret}") String adminJwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(adminJwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(String adminUsername) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(adminUsername)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TOKEN_TTL_SECONDS, ChronoUnit.SECONDS)))
                .signWith(signingKey)
                .compact();
    }
}
