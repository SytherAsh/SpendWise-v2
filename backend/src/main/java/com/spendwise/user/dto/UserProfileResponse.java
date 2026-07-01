package com.spendwise.user.dto;

import com.spendwise.user.User;

import java.time.Instant;
import java.util.UUID;

/** Response DTO — never the raw entity, per E1-S3-T1 DoD (no field beyond what's user-facing). */
public record UserProfileResponse(UUID id, String phone, String email, Instant createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.id(), user.phone(), user.email(), user.createdAt());
    }
}
