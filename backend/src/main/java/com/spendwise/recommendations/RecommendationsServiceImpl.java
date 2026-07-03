package com.spendwise.recommendations;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecommendationsServiceImpl implements RecommendationsService {

    private final RecommendationRepository recommendationRepository;

    public RecommendationsServiceImpl(RecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    @Override
    @Transactional
    public Optional<Recommendation> recordIfNoActiveRecommendationExists(
            UUID userId, Integer categoryId, String text, RecommendationPriority priority) {
        if (recommendationRepository.findActiveByUserAndCategory(userId, categoryId).isPresent()) {
            return Optional.empty();
        }
        try {
            return Optional.of(recommendationRepository.insert(userId, categoryId, text, priority));
        } catch (DuplicateKeyException e) {
            // Race between the check above and this insert -- idx_recs_user_category_active is
            // the authoritative guard (E8-S2-T1 DoD: re-run with no new crossing produces no
            // duplicate), mirroring EmiServiceImpl#createFromDetection's identical fallback.
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public List<Recommendation> listActive(UUID userId) {
        return recommendationRepository.findActiveByUser(userId);
    }

    @Override
    @Transactional
    public void dismiss(UUID userId, UUID recommendationId) {
        recommendationRepository.findById(userId, recommendationId).orElseThrow(RecommendationNotFoundException::new);
        recommendationRepository.dismiss(userId, recommendationId);
    }
}
