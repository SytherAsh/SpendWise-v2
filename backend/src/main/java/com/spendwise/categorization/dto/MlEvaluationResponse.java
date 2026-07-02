package com.spendwise.categorization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors the FastAPI {@code GET /evaluate} response body (ml/api/schemas.py EvaluationResponse)
 * exactly — the accuracy report {@link com.spendwise.categorization.CategorizationService
 * #getAccuracyMetrics} returns to its caller (Admin, in Epic 11).
 */
public record MlEvaluationResponse(
        @JsonProperty("generated_at") String generatedAt,
        @JsonProperty("n_samples") int nSamples,
        double accuracy,
        @JsonProperty("per_category") List<MlCategoryMetrics> perCategory,
        @JsonProperty("confusion_matrix") List<List<Integer>> confusionMatrix,
        @JsonProperty("confusion_matrix_labels") List<Integer> confusionMatrixLabels,
        @JsonProperty("confidence_distribution") MlConfidenceDistribution confidenceDistribution,
        @JsonProperty("report_path") String reportPath) {}
