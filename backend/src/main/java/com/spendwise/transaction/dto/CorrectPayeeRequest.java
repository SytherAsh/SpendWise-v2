package com.spendwise.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** Matches docs/api.md "PUT /transactions/:id/payee — Request" (ADR-014): {@code {"canonical_name": "Sameer Sawant"}}. */
public record CorrectPayeeRequest(@JsonProperty("canonical_name") @NotBlank String canonicalName) {}
