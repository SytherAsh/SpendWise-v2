package com.spendwise.user.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public record OnboardingRequest(
        @NotBlank String consentText,
        String appVersion,
        List<String> selectedApps,
        List<String> selectedBanks,
        BigDecimal monthlySpendEstimate) {}
