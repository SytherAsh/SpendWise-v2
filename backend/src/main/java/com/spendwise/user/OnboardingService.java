package com.spendwise.user;

import com.spendwise.user.dto.OnboardingRequest;
import com.spendwise.user.dto.OnboardingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OnboardingService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final UserPreferencesService userPreferencesService;
    private final DeviceApiKeyService deviceApiKeyService;

    public OnboardingService(
            UserRepository userRepository,
            UserConsentRepository userConsentRepository,
            UserPreferencesService userPreferencesService,
            DeviceApiKeyService deviceApiKeyService) {
        this.userRepository = userRepository;
        this.userConsentRepository = userConsentRepository;
        this.userPreferencesService = userPreferencesService;
        this.deviceApiKeyService = deviceApiKeyService;
    }

    /**
     * Re-onboarding behavior (E1-S3-T3 DoD): a user may call this endpoint more than once —
     * typically when setting up a second device, since `docs/user_flows.md` Multi-Device Flow
     * allows multiple active devices per account. Each call: (a) always writes a new {@code
     * user_consent} snapshot (consent is re-shown and re-recorded, harmless to record more than
     * once), (b) merges onboarding questionnaire fields into existing preferences rather than
     * overwriting unrelated fields (see {@link UserPreferencesService}), and (c) always issues
     * a brand-new device API key for the new device while leaving every prior device's key
     * active — it never revokes or regenerates another device's key.
     */
    @Transactional
    public OnboardingResponse onboard(UUID userId, OnboardingRequest request) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        userConsentRepository.recordConsent(userId, request.consentText(), request.appVersion());
        userPreferencesService.updatePreferences(
                userId, null, request.selectedApps(), request.selectedBanks(), request.monthlySpendEstimate());
        String rawDeviceKey = deviceApiKeyService.registerNewKey(userId);

        return new OnboardingResponse(rawDeviceKey, new OnboardingResponse.UserIdentity(user.id(), user.phone()));
    }
}
