package com.spendwise.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserPreferencesService {

    private final UserPreferencesRepository repository;

    public UserPreferencesService(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserPreferences getPreferences(UUID userId) {
        return repository.find(userId).orElseGet(() -> UserPreferences.defaults(userId));
    }

    /** Merge semantics — see {@link com.spendwise.user.dto.UpdateUserPreferencesRequest}. */
    @Transactional
    public UserPreferences updatePreferences(
            UUID userId,
            Map<String, Boolean> alertChannels,
            List<String> selectedApps,
            List<String> selectedBanks,
            BigDecimal monthlySpendEstimate) {
        UserPreferences existing = getPreferences(userId);
        return repository.upsert(
                userId,
                alertChannels != null ? alertChannels : existing.alertChannels(),
                selectedApps != null ? selectedApps : existing.selectedApps(),
                selectedBanks != null ? selectedBanks : existing.selectedBanks(),
                monthlySpendEstimate != null ? monthlySpendEstimate : existing.monthlySpendEstimate());
    }

    /** `PUT /users/me/fcm-token` (Epic 5, E5-S3-T1) — registers or rotates the device's FCM token. */
    @Transactional
    public void updateFcmToken(UUID userId, String fcmToken) {
        repository.updateFcmToken(userId, fcmToken);
    }
}
