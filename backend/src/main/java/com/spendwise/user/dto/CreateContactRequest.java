package com.spendwise.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * At least one of {@code recipientNamePattern}, {@code upiId}, or {@code phoneNumber} must be
 * present — enforced in {@link com.spendwise.user.ContactService}, not here, so the error comes
 * back as a domain-specific {@code CONTACT_MISSING_IDENTIFIER} rather than a generic 400.
 */
public record CreateContactRequest(
        @NotBlank String name,
        @NotNull String relationshipType,
        String recipientNamePattern,
        String upiId,
        String phoneNumber) {}
