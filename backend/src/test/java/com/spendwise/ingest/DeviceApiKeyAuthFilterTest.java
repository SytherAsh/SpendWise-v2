package com.spendwise.ingest;

import com.spendwise.user.DeviceApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit-level slice of the E3-S1-T1 dual-auth guard's Required Tests (docs/testing.md Ingest
 * dual-auth validation) that don't need a running server: the device-key half of the filter
 * chain, in isolation, with {@link DeviceApiKeyService} mocked. The end-to-end "valid JWT +
 * valid device key -> 200" and the device-key-service-level inactive/mismatched-user cases are
 * covered by {@link com.spendwise.user.DeviceApiKeyServiceImplTest} (E1-S4-T1, already green)
 * and by {@code IngestControllerIntegrationTest} (requires Docker/Testcontainers).
 */
class DeviceApiKeyAuthFilterTest {

    private final DeviceApiKeyService deviceApiKeyService = mock(DeviceApiKeyService.class);
    private final DeviceApiKeyAuthFilter filter = new DeviceApiKeyAuthFilter(deviceApiKeyService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingAuthenticationIsRejectedWith401() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = responseCapturingBody();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext(); // simulates UserJwtAuthFilter having left it unset (missing/invalid JWT)

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void missingDeviceKeyHeaderIsRejectedWith401() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("X-Device-Key")).willReturn(null);
        HttpServletResponse response = responseCapturingBody();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void invalidDeviceKeyIsRejectedWith401() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("X-Device-Key")).willReturn("some-key");
        given(deviceApiKeyService.validate("some-key", userId)).willReturn(false);
        HttpServletResponse response = responseCapturingBody();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void validJwtAndValidDeviceKeyProceeds() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId);
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getHeader("X-Device-Key")).willReturn("a-valid-key");
        given(deviceApiKeyService.validate("a-valid-key", userId)).willReturn(true);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    }

    private static void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private static HttpServletResponse responseCapturingBody() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        given(response.getWriter()).willReturn(new PrintWriter(new StringWriter()));
        return response;
    }
}
