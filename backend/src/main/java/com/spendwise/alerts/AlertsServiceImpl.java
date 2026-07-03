package com.spendwise.alerts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AlertsServiceImpl implements AlertsService {

    private final AlertRepository alertRepository;

    public AlertsServiceImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional
    public Optional<Alert> recordIfNotAlreadyTriggeredThisMonth(
            UUID userId, AlertType type, Integer categoryId, AlertPriority priority, Map<String, Object> payload) {
        Instant startOfMonth = YearMonth.now().atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (alertRepository.existsSince(userId, type, categoryId, startOfMonth)) {
            return Optional.empty();
        }
        return Optional.of(alertRepository.insert(userId, type, priority, payload));
    }

    @Override
    @Transactional
    public void markDelivered(UUID userId, UUID alertId) {
        alertRepository.markDelivered(userId, alertId);
    }

    @Override
    @Transactional
    public AlertPage list(UUID userId, int limit, UUID cursor, Boolean isRead) {
        Instant cursorDate = cursor == null ? null : alertRepository.findTriggeredAt(userId, cursor).orElse(null);
        UUID effectiveCursorId = cursorDate == null ? null : cursor;
        List<Alert> rows = alertRepository.findPage(userId, isRead, cursorDate, effectiveCursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Alert> page = hasMore ? rows.subList(0, limit) : rows;
        UUID nextCursor = hasMore ? page.get(page.size() - 1).id() : null;
        return new AlertPage(page, nextCursor, hasMore);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID alertId) {
        alertRepository.findById(userId, alertId).orElseThrow(AlertNotFoundException::new);
        alertRepository.markRead(userId, alertId);
    }
}
