package com.spendwise.alerts;

/**
 * Java mirror of {@code ml/training/recurring_features.py}'s {@code compute_features()} output —
 * the exact input shape the recurring-payment classifier expects
 * ({@code categorization.dto.MlRecurringPredictionRequest}). Computed by {@link
 * RecurringPaymentDetector#computeFeatures}.
 */
public record RecurringCandidateFeatures(
        int occurrenceCount,
        double intervalMeanDays,
        double intervalCv,
        double amountMean,
        double amountCv,
        double spanDays,
        double daysSinceLastOccurrence) {}
