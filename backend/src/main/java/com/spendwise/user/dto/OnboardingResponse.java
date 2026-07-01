package com.spendwise.user.dto;

import java.util.UUID;

/** Matches docs/api.md "POST /users/me/onboarding — Response" schema exactly. */
public record OnboardingResponse(String deviceApiKey, UserIdentity user) {

    public record UserIdentity(UUID id, String phone) {}
}
