package com.spendwise.transaction;

import java.util.UUID;

/**
 * One distinct (user, recipient_name, upi_id) identity to canonicalize (ML strategy phase,
 * 2026-07-13) — the deduplicated grouping unit {@code RecipientCanonicalizationJob} sends to
 * FastAPI. Either {@code recipientName} or {@code upiId} may be null (but not both, per the query
 * that produces these); the null components are preserved verbatim so the resulting canonical name
 * can be written back to exactly the transactions that share this identity.
 */
public record RecipientIdentity(UUID userId, String recipientName, String upiId) {}
