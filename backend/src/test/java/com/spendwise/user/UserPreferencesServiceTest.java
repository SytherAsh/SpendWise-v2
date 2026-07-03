package com.spendwise.user;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Required test for E1-S3-T2: PUT with {"push": false, "email": true} then GET reflects it. */
class UserPreferencesServiceTest {

    private final UserPreferencesRepository repository = mock(UserPreferencesRepository.class);
    private final UserPreferencesService service = new UserPreferencesService(repository);

    @Test
    void getPreferencesReturnsDefaultsWhenNoRowExistsYet() {
        UUID userId = UUID.randomUUID();
        given(repository.find(userId)).willReturn(Optional.empty());

        UserPreferences preferences = service.getPreferences(userId);

        assertThat(preferences.alertChannels()).isEqualTo(Map.of("push", true, "email", true));
    }

    @Test
    void updatingAlertChannelsAloneDoesNotWipeSelectedAppsFromOnboarding() {
        UUID userId = UUID.randomUUID();
        UserPreferences existing = new UserPreferences(
                userId, Map.of("push", true, "email", true), List.of("paytm", "gpay"), List.of("SBI"), new BigDecimal("15000"), null);
        given(repository.find(userId)).willReturn(Optional.of(existing));
        given(repository.upsert(any(), any(), any(), any(), any())).willAnswer(invocation -> new UserPreferences(
                userId, invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4), null));

        UserPreferences updated = service.updatePreferences(userId, Map.of("push", false, "email", true), null, null, null);

        assertThat(updated.alertChannels()).isEqualTo(Map.of("push", false, "email", true));
        assertThat(updated.selectedApps()).isEqualTo(List.of("paytm", "gpay"));
        assertThat(updated.selectedBanks()).isEqualTo(List.of("SBI"));
        assertThat(updated.monthlySpendEstimate()).isEqualByComparingTo("15000");
        verify(repository).upsert(userId, Map.of("push", false, "email", true), List.of("paytm", "gpay"), List.of("SBI"), new BigDecimal("15000"));
    }

    @Test
    void updateFcmTokenDelegatesToRepository() {
        UUID userId = UUID.randomUUID();

        service.updateFcmToken(userId, "new-token-value");

        verify(repository).updateFcmToken(userId, "new-token-value");
    }
}
