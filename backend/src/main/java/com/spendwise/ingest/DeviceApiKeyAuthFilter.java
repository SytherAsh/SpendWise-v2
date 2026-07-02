package com.spendwise.ingest;

import com.spendwise.user.DeviceApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Second half of the ingest dual-auth guard (E3-S1-T1) — runs after {@code UserJwtAuthFilter} in
 * {@link IngestSecurityConfig}'s chain and validates the {@code X-Device-Key} header against the
 * user id that filter established. Consumes {@link DeviceApiKeyService} only via its injected
 * interface (E1-S4-T1) — per CLAUDE.md, cross-module calls go through injected service
 * interfaces only.
 *
 * <p>Deliberately short-circuits every failure with an explicit 401 response here, rather than
 * relying on Spring Security's default {@code AuthenticationEntryPoint} (which — absent an
 * explicit override — answers an unauthenticated request with 403, not the 401 the dual-auth
 * guard's Definition of Done requires for a missing JWT).
 */
public class DeviceApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String DEVICE_KEY_HEADER = "X-Device-Key";

    private final DeviceApiKeyService deviceApiKeyService;

    public DeviceApiKeyAuthFilter(DeviceApiKeyService deviceApiKeyService) {
        this.deviceApiKeyService = deviceApiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof UUID userId)) {
            respondUnauthorized(response, "INVALID_ACCESS_TOKEN", "Missing or invalid access token");
            return;
        }

        String deviceKey = request.getHeader(DEVICE_KEY_HEADER);
        if (deviceKey == null || deviceKey.isBlank()) {
            respondUnauthorized(response, "MISSING_DEVICE_KEY", "X-Device-Key header is required");
            return;
        }

        if (!deviceApiKeyService.validate(deviceKey, userId)) {
            respondUnauthorized(response, "INVALID_DEVICE_KEY", "Device key is invalid, inactive, or does not belong to this user");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void respondUnauthorized(HttpServletResponse response, String errorCode, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + errorCode + "\",\"message\":\"" + message + "\",\"status\":401}");
    }
}
