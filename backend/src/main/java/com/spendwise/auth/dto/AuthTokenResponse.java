package com.spendwise.auth.dto;

import java.util.UUID;

/** Matches docs/api.md "POST /auth/otp/verify — Response" schema exactly. */
public record AuthTokenResponse(String accessToken, String refreshToken, long expiresIn, UserSummary user) {

    public record UserSummary(UUID id, String phone, String email) {}
}
