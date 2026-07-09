package com.spendwise.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Full-replace semantics (unlike {@code UpdateUserPreferencesRequest}) — every field is required. */
public record UpdateContactRequest(
        @NotBlank String name,
        @NotNull String relationshipType,
        String recipientNamePattern,
        String upiId,
        String phoneNumber) {}
