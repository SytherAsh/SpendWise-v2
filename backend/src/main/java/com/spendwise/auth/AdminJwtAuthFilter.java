package com.spendwise.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Admin session guard for {@code /api/v1/admin/**} (E1-S2-T1). A completely independent
 * Spring Security filter chain from {@link UserJwtAuthFilter}: validates only tokens signed
 * with {@code ADMIN_JWT_SECRET}. A {@code JWT_SECRET}-signed user token — even one carrying an
 * admin role claim — fails validation here because the signature won't verify against this
 * filter's key. Its own inline JJWT parsing logic; shares no validation code with
 * {@link UserJwtAuthFilter} beyond the JJWT library itself.
 */
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public AdminJwtAuthFilter(String adminJwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(adminJwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            respondUnauthorized(response);
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(header.substring(7))
                    .getPayload();
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of()));
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            respondUnauthorized(response);
        }
    }

    private void respondUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write("{\"error\":\"INVALID_ADMIN_TOKEN\",\"message\":\"Invalid or expired admin token\",\"status\":401}");
    }
}
