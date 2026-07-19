package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Mirrors the FastAPI {@code POST /normalize-recipients} response body (ml/api/schemas.py
 * NormalizeRecipientsResponse). {@code canonicalNames} maps each request entry's {@code key} to
 * its canonical recipient name. {@code ambiguousGroups} (Merge Payees feature, 2026-07-19) is
 * from the same clustering pass — anchor/candidate identity pairs considered but not confidently
 * auto-merged, for {@code RecipientCanonicalizationSweep} to persist as review candidates.
 * Defaults to an empty list so an older ML service response (missing the field entirely) still
 * deserializes without error.
 */
public record MlNormalizeRecipientsResponse(
        @JsonProperty("canonical_names") Map<String, String> canonicalNames,
        @JsonProperty("ambiguous_groups") List<MlAmbiguousGroup> ambiguousGroups) {

    public MlNormalizeRecipientsResponse {
        if (ambiguousGroups == null) {
            ambiguousGroups = List.of();
        }
    }

    /** Convenience constructor for callers/tests that don't care about ambiguous groups. */
    public MlNormalizeRecipientsResponse(Map<String, String> canonicalNames) {
        this(canonicalNames, List.of());
    }
}
