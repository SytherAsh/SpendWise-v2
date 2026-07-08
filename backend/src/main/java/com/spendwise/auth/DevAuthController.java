package com.spendwise.auth;

import com.spendwise.auth.dto.AuthTokenResponse;
import com.spendwise.user.User;
import com.spendwise.user.UserAccountService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-dev-only shortcut that mints a real SpendWise session for a seeded test user, skipping
 * the Firebase OTP/Google popup flow entirely. Exists purely so frontend iteration doesn't
 * require re-authenticating through Firebase on every session.
 *
 * <p>{@code @Profile("local")} means this controller is never registered as a bean — and the
 * endpoint is therefore unreachable (404, not just unauthenticated) — unless the app is started
 * with {@code local} active (see {@code run-local.ps1}). The shared staging {@code dev} profile
 * and {@code prod} never activate {@code local}, so this class does not exist in either. This is
 * the sole gate; nothing else about the security config depends on request-time checks for it.
 */
@RestController
@Profile("local")
public class DevAuthController {

    /** Fixed seeded identity — always the same local test user, never a real phone number. */
    private static final String DEV_USER_PHONE = "+910000000000";

    private final UserAccountService userAccountService;
    private final UserJwtService userJwtService;
    private final RefreshTokenService refreshTokenService;

    public DevAuthController(
            UserAccountService userAccountService,
            UserJwtService userJwtService,
            RefreshTokenService refreshTokenService) {
        this.userAccountService = userAccountService;
        this.userJwtService = userJwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/api/v1/auth/dev-login")
    public ResponseEntity<AuthTokenResponse> devLogin() {
        User user = userAccountService.findOrCreateByPhone(DEV_USER_PHONE);
        String accessToken = userJwtService.issueAccessToken(user.id());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.id());
        return ResponseEntity.ok(new AuthTokenResponse(
                accessToken,
                refreshToken.rawToken(),
                UserJwtService.ACCESS_TOKEN_TTL_SECONDS,
                new AuthTokenResponse.UserSummary(user.id(), user.phone(), user.email())));
    }
}
