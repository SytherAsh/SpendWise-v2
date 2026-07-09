package com.spendwise.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Counterparty metadata CRUD (ADR-010) — creation requires at least one identifier; update/delete are user-scoped. */
class ContactServiceTest {

    private final ContactRepository repository = mock(ContactRepository.class);
    private final ContactService service = new ContactService(repository);

    @Test
    void creatingAContactWithNoIdentifierIsRejected() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(userId, "Prachi", Contact.RelationshipType.FAMILY, null, null, null))
                .isInstanceOf(InvalidContactException.class);
        verify(repository, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void creatingAContactWithOnlyANameIdentifierSucceeds() {
        UUID userId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Contact created = new Contact(contactId, userId, "Prachi", Contact.RelationshipType.FAMILY, "PRACHI SAVANT", null, null, Instant.now());
        given(repository.insert(userId, "Prachi", Contact.RelationshipType.FAMILY, "PRACHI SAVANT", null, null))
                .willReturn(created);

        Contact result = service.create(userId, "Prachi", Contact.RelationshipType.FAMILY, "PRACHI SAVANT", null, null);

        assertThat(result).isEqualTo(created);
    }

    @Test
    void updatingANonexistentOrOtherUsersContactThrowsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        given(repository.update(userId, contactId, "Rahul", Contact.RelationshipType.FRIEND, null, "rahul@ybl", null))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, contactId, "Rahul", Contact.RelationshipType.FRIEND, null, "rahul@ybl", null))
                .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    void deletingANonexistentOrOtherUsersContactThrowsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        given(repository.delete(userId, contactId)).willReturn(false);

        assertThatThrownBy(() -> service.delete(userId, contactId)).isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    void listDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        Contact contact = new Contact(
                UUID.randomUUID(), userId, "Samir", Contact.RelationshipType.FAMILY, "SAMIR SAVANT", null, null, Instant.now());
        given(repository.findAllForUser(userId)).willReturn(List.of(contact));

        assertThat(service.list(userId)).containsExactly(contact);
    }
}
