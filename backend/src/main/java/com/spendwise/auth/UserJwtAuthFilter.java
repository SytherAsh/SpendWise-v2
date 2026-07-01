package com.spendwise.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * User session guard for {@code /api/v1/**} (E1-S1-T7). Validates the Bearer token against
 * {@code JWT_SECRET} only — a token signed with {@code ADMIN_JWT_SECRET} fails validation here
 * because the signature won't verify against this filter's key, regardless of any role claim
 * it carries. Deliberately does not share validation code with {@link AdminJwtAuthFilter}
 * beyond the JJWT library itself (E1-S2-T1 isolation requirement).
 */
public class UserJwtAuthFilter extends OncePerRequestFilter {

    private final UserJwtService userJwtService;

    public UserJwtAuthFilter(UserJwtService userJwtService) {
        this.userJwtService = userJwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID userId = userJwtService.validateAndGetUserId(header.substring(7));
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            filterChain.doFilter(request, response);
        } catch (InvalidUserJwtException e) {
            SecurityContextHolder.clearContext();
            respondUnauthorized(response);
        }
    }

    private void respondUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write("{\"error\":\"INVALID_ACCESS_TOKEN\",\"message\":\"Invalid or expired access token\",\"status\":401}");
    }
}
