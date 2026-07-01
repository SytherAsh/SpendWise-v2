package com.spendwise.user;

import com.spendwise.user.dto.OnboardingRequest;
import com.spendwise.user.dto.OnboardingResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Required test for E1-S3-T3: response contains the raw key; consent + device key registration both occur. */
class OnboardingServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserConsentRepository userConsentRepository = mock(UserConsentRepository.class);
    private final UserPreferencesService userPreferencesService = mock(UserPreferencesService.class);
    private final DeviceApiKeyService deviceApiKeyService = mock(DeviceApiKeyService.class);
    private final OnboardingService service =
            new OnboardingService(userRepository, userConsentRepository, userPreferencesService, deviceApiKeyService);

    @Test
    void onboardingRecordsConsentPreferencesAndReturnsRawDeviceKey() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "+911234567890", null, null, Instant.now());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(deviceApiKeyService.registerNewKey(userId)).willReturn("raw-device-key-abc");
        OnboardingRequest request = new OnboardingRequest(
                "I consent to SMS access, storage, and ML training", "1.0.0", List.of("paytm"), List.of("SBI"), null);

        OnboardingResponse response = service.onboard(userId, request);

        assertThat(response.deviceApiKey()).isEqualTo("raw-device-key-abc");
        assertThat(response.user().id()).isEqualTo(userId);
        assertThat(response.user().phone()).isEqualTo("+911234567890");
        verify(userConsentRepository)
                .recordConsent(userId, "I consent to SMS access, storage, and ML training", "1.0.0");
        verify(userPreferencesService).updatePreferences(userId, null, List.of("paytm"), List.of("SBI"), null);
    }

    @Test
    void reOnboardingIssuesANewDeviceKeyWithoutTouchingPriorOnes() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "+911234567890", null, null, Instant.now());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(deviceApiKeyService.registerNewKey(userId)).willReturn("first-key", "second-key");
        OnboardingRequest request = new OnboardingRequest("consent text", "1.0.0", List.of(), List.of(), null);

        OnboardingResponse first = service.onboard(userId, request);
        OnboardingResponse second = service.onboard(userId, request);

        assertThat(first.deviceApiKey()).isEqualTo("first-key");
        assertThat(second.deviceApiKey()).isEqualTo("second-key");
        verify(deviceApiKeyService, times(2)).registerNewKey(userId);
    }
}
