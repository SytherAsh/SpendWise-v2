package com.spendwise.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final Set<String> VALID_GRANULARITIES = Set.of("week", "month", "year");

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsServiceImpl(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Override
    @Transactional
    public AnalyticsSummary summary(UUID userId, Instant from, Instant to) {
        validateRange(from, to);
        return new AnalyticsSummary(
                analyticsRepository.overallTotals(userId, from, to), analyticsRepository.categoryTotals(userId, from, to));
    }

    @Override
    @Transactional
    public List<CategoryTotal> categoryBreakdown(UUID userId, Instant from, Instant to) {
        validateRange(from, to);
        return analyticsRepository.categoryTotals(userId, from, to);
    }

    @Override
    @Transactional
    public AnalyticsComparison comparison(UUID userId, String granularity) {
        String normalized = validateGranularity(granularity);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        LocalDate currentStart = periodStart(today, normalized);
        LocalDate currentEnd = nextPeriodStart(currentStart, normalized);
        LocalDate previousStart = previousPeriodStart(currentStart, normalized);

        ComparisonPeriod current = loadPeriod(userId, currentStart, currentEnd);
        ComparisonPeriod previous = loadPeriod(userId, previousStart, currentStart);

        return new AnalyticsComparison(normalized, current, previous);
    }

    @Override
    @Transactional
    public AnalyticsTrends trends(UUID userId, String granularity, Instant from, Instant to, Integer categoryId) {
        String normalized = validateGranularity(granularity);
        validateRange(from, to);
        return new AnalyticsTrends(normalized, analyticsRepository.trends(userId, normalized, from, to, categoryId));
    }

    @Override
    @Transactional
    public List<AnalyticsExportRow> exportRows(UUID userId, Instant from, Instant to) {
        validateRange(from, to);
        return analyticsRepository.findAllForExport(userId, from, to);
    }

    /** {@code [inclusiveStart, exclusiveEnd)} loaded as an inclusive query by subtracting 1ms off the end. */
    private ComparisonPeriod loadPeriod(UUID userId, LocalDate inclusiveStart, LocalDate exclusiveEnd) {
        Instant from = inclusiveStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = exclusiveEnd.atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
        return new ComparisonPeriod(
                from, to, analyticsRepository.overallTotals(userId, from, to), analyticsRepository.categoryTotals(userId, from, to));
    }

    private static LocalDate periodStart(LocalDate today, String granularity) {
        return switch (granularity) {
            case "week" -> today.with(DayOfWeek.MONDAY);
            case "month" -> YearMonth.from(today).atDay(1);
            case "year" -> LocalDate.of(today.getYear(), 1, 1);
            default -> throw new IllegalStateException("unreachable: granularity already validated");
        };
    }

    private static LocalDate nextPeriodStart(LocalDate periodStart, String granularity) {
        return switch (granularity) {
            case "week" -> periodStart.plusWeeks(1);
            case "month" -> periodStart.plusMonths(1);
            case "year" -> periodStart.plusYears(1);
            default -> throw new IllegalStateException("unreachable: granularity already validated");
        };
    }

    private static LocalDate previousPeriodStart(LocalDate periodStart, String granularity) {
        return switch (granularity) {
            case "week" -> periodStart.minusWeeks(1);
            case "month" -> periodStart.minusMonths(1);
            case "year" -> periodStart.minusYears(1);
            default -> throw new IllegalStateException("unreachable: granularity already validated");
        };
    }

    private static String validateGranularity(String granularity) {
        if (granularity == null || granularity.isBlank()) {
            return "month";
        }
        String normalized = granularity.toLowerCase();
        if (!VALID_GRANULARITIES.contains(normalized)) {
            throw new InvalidAnalyticsQueryException("granularity must be one of week, month, year");
        }
        return normalized;
    }

    private static void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new InvalidAnalyticsQueryException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new InvalidAnalyticsQueryException("from must not be after to");
        }
    }
}
