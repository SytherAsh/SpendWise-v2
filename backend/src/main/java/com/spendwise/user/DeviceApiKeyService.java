package com.spendwise.user;

import java.util.UUID;

/**
 * Device API key registration (E1-S3-T3) and validation (E1-S4-T1). Consumed by the Ingest
 * module (Epic 3) via this injected interface to validate the {@code X-Device-Key} header —
 * per CLAUDE.md, cross-module calls go through injected service interfaces only.
 */
public interface DeviceApiKeyService {

    /** Generates, hashes, and persists a new device key for {@code userId}; returns the raw key exactly once. */
    String registerNewKey(UUID userId);

    /**
     * @return {@code true} only if {@code rawKey} hashes to an active key row belonging to
     *     {@code userId}; updates {@code last_used_at} on success. Missing, inactive, or
     *     mismatched-user keys all return {@code false} — never throws for a bad key.
     */
    boolean validate(String rawKey, UUID userId);
}
