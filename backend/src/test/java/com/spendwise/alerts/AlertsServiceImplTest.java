package com.spendwise.alerts;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Required tests for E5-S2-T3 (suppression) and E5-S4 (pagination, mark-read ownership). */
class AlertsServiceImplTest {

    private final AlertRepository alertRepository = mock(AlertRepository.class);
    private final AlertsServiceImpl service = new AlertsServiceImpl(alertRepository);
    private final UUID userId = UUID.randomUUID();

    @Test
    void suppressesAnAlertOfTheSameTypeAndCategoryAlreadyTriggeredThisMonth() {
        given(alertRepository.existsSince(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(7), any())).willReturn(true);

        Optional<Alert> result =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.CATEGORY_OVERSPEND, 7, AlertPriority.HIGH, Map.of());

        assertThat(result).isEmpty();
        verify(alertRepository, never()).insert(any(), any(), any(), any());
    }

    @Test
    void runningTheEvaluatorTwiceDoesNotCreateASecondAlert() {
        given(alertRepository.existsSince(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(7), any()))
                .willReturn(false)
                .willReturn(true);
        given(alertRepository.insert(eq(userId), eq(AlertType.CATEGORY_OVERSPEND), eq(AlertPriority.HIGH), any()))
                .willReturn(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH));

        Optional<Alert> first =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.CATEGORY_OVERSPEND, 7, AlertPriority.HIGH, Map.of());
        Optional<Alert> second =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.CATEGORY_OVERSPEND, 7, AlertPriority.HIGH, Map.of());

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
    }

    @Test
    void recordsANewAlertWhenNoneAlreadyTriggeredThisMonth() {
        given(alertRepository.existsSince(eq(userId), eq(AlertType.MID_MONTH_BUDGET), eq(null), any())).willReturn(false);
        given(alertRepository.insert(eq(userId), eq(AlertType.MID_MONTH_BUDGET), eq(AlertPriority.HIGH), any()))
                .willReturn(sampleAlert(AlertType.MID_MONTH_BUDGET, AlertPriority.HIGH));

        Optional<Alert> result =
                service.recordIfNotAlreadyTriggeredThisMonth(userId, AlertType.MID_MONTH_BUDGET, null, AlertPriority.HIGH, Map.of());

        assertThat(result).isPresent();
    }

    @Test
    void markReadThrowsNotFoundForMissingOrForeignAlert() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.markRead(userId, alertId));
    }

    @Test
    void markReadDoesNotTouchDeliveredAt() {
        UUID alertId = UUID.randomUUID();
        given(alertRepository.findById(userId, alertId)).willReturn(Optional.of(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH)));

        service.markRead(userId, alertId);

        verify(alertRepository).markRead(userId, alertId);
        verify(alertRepository, never()).markDelivered(any(), any());
    }

    @Test
    void listFirstPageDoesNotResolveACursor() {
        given(alertRepository.findPage(eq(userId), eq(null), eq(null), eq(null), eq(21)))
                .willReturn(List.of(sampleAlert(AlertType.CATEGORY_OVERSPEND, AlertPriority.HIGH)));

        AlertPage page = service.list(userId, 20, null, null);

        verify(alertRepository, never()).findTriggeredAt(any(), any());
        assertThat(page.hasMore()).isFalse();
        assertThat(page.data()).hasSize(1);
    }

    @Test
    void listUnreadFilterIsPassedThrough() {
        given(alertRepository.findPage(eq(userId), eq(true), eq(null), eq(null), anyInt())).willReturn(List.of());

        service.list(userId, 20, null, true);

        verify(alertRepository).findPage(userId, true, null, null, 21);
    }

    private Alert sampleAlert(AlertType type, AlertPriority priority) {
        return new Alert(UUID.randomUUID(), userId, type, priority, Instant.now(), null, false, Map.of());
    }
}
