package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Mirrors the FastAPI {@code POST /normalize-recipients} response body (ml/api/schemas.py
 * NormalizeRecipientsResponse). {@code canonicalNames} maps each request entry's {@code key} to
 * its canonical recipient name.
 */
public record MlNormalizeRecipientsResponse(@JsonProperty("canonical_names") Map<String, String> canonicalNames) {}
