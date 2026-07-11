package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors the FastAPI {@code POST /retrain-recurring} response body (ml/api/schemas.py RecurringRetrainResponse). */
public record MlRecurringRetrainResponse(
        String status, @JsonProperty("trained_candidate_groups") int trainedCandidateGroups) {}
