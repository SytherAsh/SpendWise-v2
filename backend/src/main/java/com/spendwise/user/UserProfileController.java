package com.spendwise.user;

import com.spendwise.user.dto.UpdateUserProfileRequest;
import com.spendwise.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public UserProfileResponse getProfile(@AuthenticationPrincipal UUID userId) {
        return UserProfileResponse.from(userProfileService.getProfile(userId));
    }

    @PutMapping
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateUserProfileRequest request) {
        return UserProfileResponse.from(userProfileService.updateEmail(userId, request.email()));
    }
}
