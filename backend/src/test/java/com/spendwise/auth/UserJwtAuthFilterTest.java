package com.spendwise.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Required tests for E1-S1-T7: a JWT_SECRET-signed token passes; an ADMIN_JWT_SECRET-signed
 * token is rejected by this filter even though it is a well-formed, non-expired JWT.
 */
class UserJwtAuthFilterTest {

    private static final String USER_SECRET = "user-secret-value-1234567890-abcdef";
    private static final String ADMIN_SECRET = "admin-secret-value-0987654321-fedcba";

    private final UserJwtService userJwtService = new UserJwtService(USER_SECRET);
    private final UserJwtAuthFilter filter = new UserJwtAuthFilter(userJwtService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userSecretSignedTokenPasses() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = userJwtService.issueAccessToken(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn("Bearer " + token);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    }

    @Test
    void adminSecretSignedTokenIsRejected() throws Exception {
        String adminToken = adminSignedToken(ADMIN_SECRET, UUID.randomUUID().toString());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("Authorization")).willReturn("Bearer " + adminToken);
        given(response.getWriter()).willReturn(new java.io.PrintWriter(new java.io.StringWriter()));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static String adminSignedToken(String secret, String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }
}
