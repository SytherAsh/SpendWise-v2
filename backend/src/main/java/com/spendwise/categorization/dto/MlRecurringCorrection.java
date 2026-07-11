package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors one entry of ml/api/schemas.py's {@code RecurringCorrection} — a candidate group's
 * features plus the user's confirm/dismiss outcome, read from {@code recurring_corrections}. */
public record MlRecurringCorrection(
        @JsonProperty("occurrence_count") int occurrenceCount,
        @JsonProperty("interval_mean_days") double intervalMeanDays,
        @JsonProperty("interval_cv") double intervalCv,
        @JsonProperty("amount_mean") double amountMean,
        @JsonProperty("amount_cv") double amountCv,
        @JsonProperty("span_days") double spanDays,
        @JsonProperty("days_since_last_occurrence") double daysSinceLastOccurrence,
        @JsonProperty("was_recurring") boolean wasRecurring) {}
