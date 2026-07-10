package com.spendwise.auth;

import com.spendwise.auth.dto.AuthTokenResponse;
import com.spendwise.user.User;
import com.spendwise.user.UserAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo account login endpoint — mints a real SpendWise session for the pre-seeded demo account
 * (see {@link com.spendwise.ingest.DemoDataSeeder}), skipping Firebase OTP entirely. Public and
 * unauthenticated by design: a single click from the landing page's "Try Demo" button.
 *
 * <p>Same token-issuance shape as {@link DevAuthController}, but not {@code @Profile}-gated — the
 * demo account is a public marketing feature, available in every environment where
 * {@code demo.enabled=true} (see application.yml).
 */
@RestController
@RequestMapping("/api/v1/auth/demo")
public class DemoAuthController {

    private final UserAccountService userAccountService;
    private final UserJwtService userJwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${demo.phone:+919876543210}")
    private String demoPhone;

    public DemoAuthController(
            UserAccountService userAccountService,
            UserJwtService userJwtService,
            RefreshTokenService refreshTokenService) {
        this.userAccountService = userAccountService;
        this.userJwtService = userJwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Login as the demo user without OTP or credentials. Idempotent — finds the demo user seeded
     * at startup, or creates it on demand if seeding hasn't run yet (e.g. demo.enabled was
     * flipped on after startup).
     */
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> loginAsDemo() {
        User user = userAccountService.findOrCreateByPhone(demoPhone);
        String accessToken = userJwtService.issueAccessToken(user.id());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.id());
        return ResponseEntity.ok(new AuthTokenResponse(
                accessToken,
                refreshToken.rawToken(),
                UserJwtService.ACCESS_TOKEN_TTL_SECONDS,
                new AuthTokenResponse.UserSummary(user.id(), user.phone(), user.email())));
    }

    /** Demo account description for the landing page — no login performed. */
    @PostMapping("/info")
    public ResponseEntity<DemoInfoResponse> getDemoInfo() {
        return ResponseEntity.ok(new DemoInfoResponse(
                "SpendWise Demo Account",
                "Explore SpendWise with realistic sample data",
                "12-month transaction history with salary, car EMI, subscriptions, daily spending, and family transfers",
                "Food Rs.10k, Travel Rs.7k, Transfers Rs.10k, Shopping Rs.5k",
                "All features enabled: categorization, budgets, alerts, analytics, export, chatbot, recommendations"));
    }

    public record DemoInfoResponse(
            String title, String subtitle, String description, String budgets, String features) {}
}
