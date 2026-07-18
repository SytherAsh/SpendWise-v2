package com.spendwise.categorization.dto;

import java.util.List;

/**
 * Mirrors the FastAPI {@code POST /normalize-recipients} request body (ml/api/schemas.py
 * NormalizeRecipientsRequest). Every entry must belong to one user — the clustering algorithm
 * compares each entry against every other, so {@code RecipientCanonicalizationJob} calls this once
 * per user, never with multiple users' recipients mixed together.
 */
public record MlNormalizeRecipientsRequest(List<MlNormalizeEntry> entries) {}
