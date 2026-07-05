package com.spendwise.admin;

import com.spendwise.admin.dto.AdminLoginRequest;
import com.spendwise.admin.dto.AdminLoginResponse;
import com.spendwise.auth.AdminJwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin login flow (E11-S1-T1) — a real gap found during Epic 11 grounding: {@code
 * AdminJwtAuthFilter}/{@code SecurityConfig} (E1-S2-T1) validate an admin-signed token, but
 * nothing in the codebase ever issued one. A single seeded admin credential (env-configured,
 * never a regular user account with a role claim — CLAUDE.md's explicit requirement) is checked
 * here and exchanged for an {@link AdminJwtService}-issued token.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminLoginRateLimiter rateLimiter;
    private final AdminJwtService adminJwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String adminUsername;
    private final String adminPasswordHash;

    public AdminAuthController(
            AdminLoginRateLimiter rateLimiter,
            AdminJwtService adminJwtService,
            @Value("${app.security.admin-username}") String adminUsername,
            @Value("${app.security.admin-password-hash}") String adminPasswordHash) {
        this.rateLimiter = rateLimiter;
        this.adminJwtService = adminJwtService;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        rateLimiter.checkAndRecord(httpRequest.getRemoteAddr());

        if (!adminUsername.equals(request.username()) || !passwordEncoder.matches(request.password(), adminPasswordHash)) {
            throw new InvalidAdminCredentialsException();
        }

        String accessToken = adminJwtService.issueAccessToken(adminUsername);
        return ResponseEntity.ok(new AdminLoginResponse(accessToken, AdminJwtService.ACCESS_TOKEN_TTL_SECONDS));
    }
}
