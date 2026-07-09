package com.spendwise.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Counterparty metadata CRUD (ADR-010 / docs/architecture.md "Future Enhancement: Counterparty
 * Metadata Enrichment"). Not consumed cross-module — the frontend fetches contacts via {@link
 * ContactController} and matches them against transactions client-side, so this stays a plain
 * service rather than an injected interface like {@link DeviceApiKeyService}.
 */
@Service
public class ContactService {

    private final ContactRepository repository;

    public ContactService(ContactRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<Contact> list(UUID userId) {
        return repository.findAllForUser(userId);
    }

    @Transactional
    public Contact create(
            UUID userId,
            String name,
            Contact.RelationshipType relationshipType,
            String recipientNamePattern,
            String upiId,
            String phoneNumber) {
        requireIdentifier(recipientNamePattern, upiId, phoneNumber);
        return repository.insert(userId, name, relationshipType, recipientNamePattern, upiId, phoneNumber);
    }

    @Transactional
    public Contact update(
            UUID userId,
            UUID id,
            String name,
            Contact.RelationshipType relationshipType,
            String recipientNamePattern,
            String upiId,
            String phoneNumber) {
        requireIdentifier(recipientNamePattern, upiId, phoneNumber);
        return repository
                .update(userId, id, name, relationshipType, recipientNamePattern, upiId, phoneNumber)
                .orElseThrow(ContactNotFoundException::new);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (!repository.delete(userId, id)) {
            throw new ContactNotFoundException();
        }
    }

    private static void requireIdentifier(String recipientNamePattern, String upiId, String phoneNumber) {
        boolean hasIdentifier = isPresent(recipientNamePattern) || isPresent(upiId) || isPresent(phoneNumber);
        if (!hasIdentifier) {
            throw new InvalidContactException();
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
