package com.spendwise.user;

import com.spendwise.user.dto.UpdateUserPreferencesRequest;
import com.spendwise.user.dto.UserPreferencesResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    public UserPreferencesController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping
    public UserPreferencesResponse getPreferences(@AuthenticationPrincipal UUID userId) {
        return UserPreferencesResponse.from(userPreferencesService.getPreferences(userId));
    }

    @PutMapping
    public UserPreferencesResponse updatePreferences(
            @AuthenticationPrincipal UUID userId, @RequestBody UpdateUserPreferencesRequest request) {
        UserPreferences updated = userPreferencesService.updatePreferences(
                userId,
                request.alertChannels(),
                request.selectedApps(),
                request.selectedBanks(),
                request.monthlySpendEstimate());
        return UserPreferencesResponse.from(updated);
    }
}
