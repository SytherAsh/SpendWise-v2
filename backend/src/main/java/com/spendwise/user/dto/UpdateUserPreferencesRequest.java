package com.spendwise.user.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Partial-update semantics: any field left {@code null} keeps its current persisted value
 * (or the schema default, if none is persisted yet) rather than being wiped — so toggling
 * {@code alertChannels} alone doesn't erase onboarding-set {@code selectedApps}/{@code
 * selectedBanks}.
 */
public record UpdateUserPreferencesRequest(
        Map<String, Boolean> alertChannels,
        List<String> selectedApps,
        List<String> selectedBanks,
        BigDecimal monthlySpendEstimate) {}
