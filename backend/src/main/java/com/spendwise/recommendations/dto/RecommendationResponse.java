package com.spendwise.recommendations.dto;

import com.spendwise.recommendations.Recommendation;

import java.time.Instant;
import java.util.UUID;

public record RecommendationResponse(UUID id, Integer categoryId, String text, String priority, Instant generatedAt) {

    public static RecommendationResponse from(Recommendation recommendation) {
        return new RecommendationResponse(
                recommendation.id(),
                recommendation.categoryId(),
                recommendation.text(),
                recommendation.priority().dbValue(),
                recommendation.generatedAt());
    }
}
