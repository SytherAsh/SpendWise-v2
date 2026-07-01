package com.spendwise.user;

import java.time.Instant;
import java.util.UUID;

public record DeviceApiKey(UUID id, UUID userId, String keyHash, Instant registeredAt, Instant lastUsedAt, boolean isActive) {}
