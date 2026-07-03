package com.spendwise.recommendations;

import java.time.Instant;
import java.util.UUID;

/** {@code categoryId} is null for a global (cross-category) recommendation — not produced by E8-S2-T1. */
public record Recommendation(
        UUID id, UUID userId, Integer categoryId, String text, RecommendationPriority priority, Instant generatedAt, boolean isDismissed) {}
