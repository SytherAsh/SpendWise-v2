package com.spendwise.user.dto;

import com.spendwise.user.UserPreferences;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record UserPreferencesResponse(
        Map<String, Boolean> alertChannels,
        List<String> selectedApps,
        List<String> selectedBanks,
        BigDecimal monthlySpendEstimate) {

    public static UserPreferencesResponse from(UserPreferences preferences) {
        return new UserPreferencesResponse(
                preferences.alertChannels(),
                preferences.selectedApps(),
                preferences.selectedBanks(),
                preferences.monthlySpendEstimate());
    }
}
