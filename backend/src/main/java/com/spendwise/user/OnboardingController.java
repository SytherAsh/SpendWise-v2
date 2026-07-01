package com.spendwise.user;

import com.spendwise.user.dto.OnboardingRequest;
import com.spendwise.user.dto.OnboardingResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    public OnboardingResponse onboard(@AuthenticationPrincipal UUID userId, @Valid @RequestBody OnboardingRequest request) {
        return onboardingService.onboard(userId, request);
    }
}
