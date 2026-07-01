package com.spendwise.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(@NotBlank String phone) {}
