package com.spendwise.categorization.dto;

/**
 * One candidate FastAPI's clustering considered but did not confidently auto-merge into the
 * containing {@link MlAmbiguousGroup}'s anchor (ml/api/schemas.py {@code AmbiguousCandidate}) —
 * Merge Payees feature, ML strategy phase, 2026-07-19. {@code key} is the same synthetic
 * per-user index {@link MlNormalizeEntry} sent, so {@code RecipientCanonicalizationSweep} can map
 * it back to the exact {@code RecipientIdentity} it refers to.
 */
public record MlAmbiguousCandidate(String key, String name, int score, String reason) {}
