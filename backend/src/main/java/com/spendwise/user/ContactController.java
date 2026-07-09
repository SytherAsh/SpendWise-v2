package com.spendwise.user;

import com.spendwise.user.dto.ContactResponse;
import com.spendwise.user.dto.CreateContactRequest;
import com.spendwise.user.dto.UpdateContactRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public List<ContactResponse> list(@AuthenticationPrincipal UUID userId) {
        return contactService.list(userId).stream().map(ContactResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ContactResponse> create(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateContactRequest request) {
        Contact created = contactService.create(
                userId,
                request.name(),
                parseRelationshipType(request.relationshipType()),
                request.recipientNamePattern(),
                request.upiId(),
                request.phoneNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(ContactResponse.from(created));
    }

    @PutMapping("/{id}")
    public ContactResponse update(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id, @Valid @RequestBody UpdateContactRequest request) {
        Contact updated = contactService.update(
                userId,
                id,
                request.name(),
                parseRelationshipType(request.relationshipType()),
                request.recipientNamePattern(),
                request.upiId(),
                request.phoneNumber());
        return ContactResponse.from(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        contactService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private static Contact.RelationshipType parseRelationshipType(String value) {
        try {
            return Contact.RelationshipType.fromDbValue(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidRelationshipTypeException(value);
        }
    }
}
