package com.spendwise.user;

import java.time.Instant;
import java.util.UUID;

public record Contact(
        UUID id,
        UUID userId,
        String name,
        RelationshipType relationshipType,
        String recipientNamePattern,
        String upiId,
        String phoneNumber,
        Instant createdAt) {

    /** Counterparty-type metadata per ADR-010 — deliberately not a spending category. */
    public enum RelationshipType {
        FAMILY,
        FRIEND,
        SELF,
        SETTLEMENT;

        public String dbValue() {
            return name().toLowerCase();
        }

        public static RelationshipType fromDbValue(String value) {
            return RelationshipType.valueOf(value.toUpperCase());
        }
    }
}
