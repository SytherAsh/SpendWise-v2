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
    // trends() has no periodStart/nextPeriodStart notion (unlike comparison(), which anchors
    // "day" would be meaningless there) -- it forwards granularity straight into a SQL
    // date_trunc call (AnalyticsRepository.trends), which natively supports 'day'. Added after
    // local E2E testing found the Android dashboard's 30-day daily trend line (E9-S2-T1) 400s
    // against this endpoint -- week/month/year alone can't render a meaningful 30-day trend
    // (docs/api.md's granularity note was written before that client existed).
    private static final Set<String> VALID_TRENDS_GRANULARITIES = Set.of("day", "week", "month", "year");

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
        String normalized = validateGranularity(granularity, VALID_GRANULARITIES);
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
        String normalized = validateGranularity(granularity, VALID_TRENDS_GRANULARITIES);
        validateRange(from, to);
        return new AnalyticsTrends(normalized, analyticsRepository.trends(userId, normalized, from, to, categoryId));
    }

    @Override
    @Transactional
    public List<AnalyticsExportRow> exportRows(UUID userId, Instant from, Instant to) {
        validateRange(from, to);
        return analyticsRepository.findAllForExport(userId, from, to);
    }

    @Override
    public List<CategoryMonthSpend> findAllCategorySpendForMonth(int month, int year) {
        // No @Transactional / RlsSession here -- reads via the spendwise_jobs DataSource
        // (BYPASSRLS), mirroring TransactionServiceImpl.findAllSpendForMonth.
        return analyticsRepository.findAllCategorySpendForMonth(month, year);
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

    private static String validateGranularity(String granularity, Set<String> validValues) {
        if (granularity == null || granularity.isBlank()) {
            return "month";
        }
        String normalized = granularity.toLowerCase();
        if (!validValues.contains(normalized)) {
            throw new InvalidAnalyticsQueryException("granularity must be one of " + String.join(", ", validValues.stream().sorted().toList()));
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
