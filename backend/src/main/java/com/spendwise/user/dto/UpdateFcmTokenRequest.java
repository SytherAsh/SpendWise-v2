package com.spendwise.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFcmTokenRequest(@NotBlank String fcmToken) {}
