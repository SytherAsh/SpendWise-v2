package com.spendwise.user.dto;

import jakarta.validation.constraints.Email;

/**
 * Email is the only user-editable identity field on {@code users} — phone and google_id are
 * login-identity anchors set at account creation, not self-service editable (E1-S3-T1 scope
 * decision).
 */
public record UpdateUserProfileRequest(@Email String email) {}
