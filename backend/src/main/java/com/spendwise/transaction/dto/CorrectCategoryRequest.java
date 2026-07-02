package com.spendwise.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** Matches docs/api.md "PUT /transactions/:id/category — Request" exactly: {@code {"category_id": 7}}. */
public record CorrectCategoryRequest(@JsonProperty("category_id") @NotNull Integer categoryId) {}
