package com.spendwise.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UserPreferences(
        UUID userId,
        Map<String, Boolean> alertChannels,
        List<String> selectedApps,
        List<String> selectedBanks,
        BigDecimal monthlySpendEstimate) {

    public static UserPreferences defaults(UUID userId) {
        return new UserPreferences(userId, Map.of("push", true, "email", true), List.of(), List.of(), null);
    }
}
