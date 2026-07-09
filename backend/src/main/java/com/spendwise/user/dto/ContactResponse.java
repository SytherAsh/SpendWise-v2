package com.spendwise.user.dto;

import com.spendwise.user.Contact;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        String name,
        String relationshipType,
        String recipientNamePattern,
        String upiId,
        String phoneNumber,
        Instant createdAt) {

    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
                contact.id(),
                contact.name(),
                contact.relationshipType().dbValue(),
                contact.recipientNamePattern(),
                contact.upiId(),
                contact.phoneNumber(),
                contact.createdAt());
    }
}
