package com.spendwise.recommendations;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RecommendationsServiceImplTest {

    private final RecommendationRepository recommendationRepository = mock(RecommendationRepository.class);
    private final RecommendationsServiceImpl service = new RecommendationsServiceImpl(recommendationRepository);
    private final UUID userId = UUID.randomUUID();

    @Test
    void recordsWhenNoActiveRecommendationExistsForThatUserAndCategory() {
        given(recommendationRepository.findActiveByUserAndCategory(userId, 1)).willReturn(Optional.empty());
        Recommendation inserted =
                new Recommendation(UUID.randomUUID(), userId, 1, "text", RecommendationPriority.MEDIUM, Instant.now(), false);
        given(recommendationRepository.insert(userId, 1, "text", RecommendationPriority.MEDIUM)).willReturn(inserted);

        Optional<Recommendation> result = service.recordIfNoActiveRecommendationExists(userId, 1, "text", RecommendationPriority.MEDIUM);

        assertThat(result).contains(inserted);
    }

    @Test
    void suppressesWhenAnActiveRecommendationAlreadyExistsForThatUserAndCategory() {
        Recommendation existing =
                new Recommendation(UUID.randomUUID(), userId, 1, "existing", RecommendationPriority.MEDIUM, Instant.now(), false);
        given(recommendationRepository.findActiveByUserAndCategory(userId, 1)).willReturn(Optional.of(existing));

        Optional<Recommendation> result = service.recordIfNoActiveRecommendationExists(userId, 1, "text", RecommendationPriority.MEDIUM);

        assertThat(result).isEmpty();
        verify(recommendationRepository, never()).insert(any(), any(), any(), any());
    }

    @Test
    void aRaceOnInsertIsCaughtAndTreatedAsSuppressed() {
        given(recommendationRepository.findActiveByUserAndCategory(userId, 1)).willReturn(Optional.empty());
        given(recommendationRepository.insert(eq(userId), eq(1), any(), any())).willThrow(new DuplicateKeyException("race"));

        Optional<Recommendation> result = service.recordIfNoActiveRecommendationExists(userId, 1, "text", RecommendationPriority.MEDIUM);

        assertThat(result).isEmpty();
    }

    @Test
    void listActiveDelegatesToTheRepository() {
        List<Recommendation> expected =
                List.of(new Recommendation(UUID.randomUUID(), userId, 1, "text", RecommendationPriority.MEDIUM, Instant.now(), false));
        given(recommendationRepository.findActiveByUser(userId)).willReturn(expected);

        assertThat(service.listActive(userId)).isEqualTo(expected);
    }

    @Test
    void dismissThrowsWhenAbsentOrOwnedByAnotherUser() {
        UUID id = UUID.randomUUID();
        given(recommendationRepository.findById(userId, id)).willReturn(Optional.empty());

        assertThrows(RecommendationNotFoundException.class, () -> service.dismiss(userId, id));
        verify(recommendationRepository, never()).dismiss(any(), any());
    }

    @Test
    void dismissMarksTheRecommendationDismissedWhenItExists() {
        UUID id = UUID.randomUUID();
        Recommendation existing = new Recommendation(id, userId, 1, "text", RecommendationPriority.MEDIUM, Instant.now(), false);
        given(recommendationRepository.findById(userId, id)).willReturn(Optional.of(existing));

        service.dismiss(userId, id);

        verify(recommendationRepository).dismiss(userId, id);
    }
}
