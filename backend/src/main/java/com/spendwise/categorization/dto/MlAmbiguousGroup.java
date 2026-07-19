package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One anchor identity plus its ambiguous candidates (ml/api/schemas.py {@code AmbiguousGroup})
 * — Merge Payees feature, ML strategy phase, 2026-07-19. */
public record MlAmbiguousGroup(
        @JsonProperty("anchor_key") String anchorKey,
        @JsonProperty("anchor_name") String anchorName,
        List<MlAmbiguousCandidate> candidates) {}
