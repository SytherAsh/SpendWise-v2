package com.spendwise.recommendations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationsService {

    /**
     * Records a new active recommendation for {@code userId}+{@code categoryId} unless one is
     * already active (E8-S2-T1 idempotency — {@code idx_recs_user_category_active} allows at most
     * one active row per user+category). Returns empty when suppressed; the generator job simply
     * moves on to the next candidate either way.
     */
    Optional<Recommendation> recordIfNoActiveRecommendationExists(
            UUID userId, Integer categoryId, String text, RecommendationPriority priority);

    /** E8-S2-T2 feed — active recommendations, newest-first. */
    List<Recommendation> listActive(UUID userId);

    /** @throws RecommendationNotFoundException if absent or owned by a different user */
    void dismiss(UUID userId, UUID recommendationId);
}
