package com.spendwise.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Required test for E1-S2-T1: an ADMIN_JWT_SECRET-signed token is accepted; a
 * JWT_SECRET-signed user token — even carrying an admin role claim — is rejected by this
 * filter, per docs/testing.md "Admin route rejects a JWT_SECRET-signed token even with an
 * admin role claim."
 */
class AdminJwtAuthFilterTest {

    private static final String ADMIN_SECRET = "admin-secret-value-0987654321-fedcba";
    private static final String USER_SECRET = "user-secret-value-1234567890-abcdef";

    private final AdminJwtAuthFilter filter = new AdminJwtAuthFilter(ADMIN_SECRET);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminSecretSignedTokenPasses() throws Exception {
        String token = signedToken(ADMIN_SECRET, "admin-1", Map.of());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("admin-1");
    }

    @Test
    void userSecretSignedTokenIsRejectedEvenWithAdminRoleClaim() throws Exception {
        String token = signedToken(USER_SECRET, UUID.randomUUID().toString(), Map.of("role", "admin"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);
        given(response.getWriter()).willReturn(new PrintWriter(new StringWriter()));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
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
