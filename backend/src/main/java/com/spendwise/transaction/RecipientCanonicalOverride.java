package com.spendwise.transaction;

import java.util.UUID;

/**
 * One row of {@code recipient_canonicalization_overrides} (ADR-014) — a user-pinned canonical
 * name for a (user, recipient_name, upi_id) identity that must win over whatever
 * {@code RecipientCanonicalizationSweep}'s ML clustering call computes for it.
 */
public record RecipientCanonicalOverride(UUID userId, String recipientName, String upiId, String canonicalName) {}
