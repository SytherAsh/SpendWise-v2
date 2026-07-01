package com.spendwise.auth;

import com.spendwise.auth.dto.AuthTokenResponse;
import com.spendwise.auth.dto.GoogleLoginRequest;
import com.spendwise.auth.dto.LogoutRequest;
import com.spendwise.auth.dto.OtpSendRequest;
import com.spendwise.auth.dto.OtpVerifyRequest;
import com.spendwise.auth.dto.TokenRefreshRequest;
import com.spendwise.auth.dto.TokenRefreshResponse;
import com.spendwise.user.User;
import com.spendwise.user.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final OtpSendRateLimiter otpSendRateLimiter;
    private final FirebaseAuthService firebaseAuthService;
    private final UserAccountService userAccountService;
    private final UserJwtService userJwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            OtpSendRateLimiter otpSendRateLimiter,
            FirebaseAuthService firebaseAuthService,
            UserAccountService userAccountService,
            UserJwtService userJwtService,
            RefreshTokenService refreshTokenService) {
        this.otpSendRateLimiter = otpSendRateLimiter;
        this.firebaseAuthService = firebaseAuthService;
        this.userAccountService = userAccountService;
        this.userJwtService = userJwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Actual OTP delivery happens client-side via the Firebase client SDK (see FirebaseConfig);
     * this endpoint's job is the server-side rate-limit backstop (docs/security.md).
     */
    @PostMapping("/otp/send")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        otpSendRateLimiter.checkAndRecord(request.phone());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthTokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        FirebaseVerifiedIdentity identity;
        try {
            identity = firebaseAuthService.verifyIdToken(request.idToken());
        } catch (InvalidFirebaseTokenException e) {
            throw new InvalidOtpException("OTP session is invalid or expired", e);
        }

        String phone = identity.phoneNumber() != null ? identity.phoneNumber() : request.phone();
        User user = userAccountService.findOrCreateByPhone(phone);
        return ResponseEntity.ok(issueTokens(user));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthTokenResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        FirebaseVerifiedIdentity identity = firebaseAuthService.verifyIdToken(request.idToken());
        User user = userAccountService.findOrCreateByGoogleId(identity.uid(), identity.email());
        return ResponseEntity.ok(issueTokens(user));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        String accessToken = userJwtService.issueAccessToken(rotation.userId());
        return ResponseEntity.ok(new TokenRefreshResponse(
                accessToken, rotation.newRawToken(), UserJwtService.ACCESS_TOKEN_TTL_SECONDS));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UUID userId, @Valid @RequestBody LogoutRequest request) {
        refreshTokenService.logout(request.refreshToken(), userId);
        return ResponseEntity.noContent().build();
    }

    private AuthTokenResponse issueTokens(User user) {
        String accessToken = userJwtService.issueAccessToken(user.id());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.id());
        return new AuthTokenResponse(
                accessToken,
                refreshToken.rawToken(),
                UserJwtService.ACCESS_TOKEN_TTL_SECONDS,
                new AuthTokenResponse.UserSummary(user.id(), user.phone(), user.email()));
    }
}
