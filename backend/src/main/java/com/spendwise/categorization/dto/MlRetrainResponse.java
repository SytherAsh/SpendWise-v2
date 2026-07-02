package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors the FastAPI {@code POST /retrain} response body (ml/api/schemas.py RetrainResponse). */
public record MlRetrainResponse(String status, @JsonProperty("trained_samples") int trainedSamples) {}
