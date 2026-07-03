package com.spendwise.user;

import com.spendwise.user.dto.UpdateFcmTokenRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** docs/api.md "PUT /users/me/fcm-token" — added Epic 5 so Alerts' push dispatch has a device to target. */
@RestController
@RequestMapping("/api/v1/users/me/fcm-token")
public class FcmTokenController {

    private final UserPreferencesService userPreferencesService;

    public FcmTokenController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @PutMapping
    public void update(@AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateFcmTokenRequest request) {
        userPreferencesService.updateFcmToken(userId, request.fcmToken());
    }
}
