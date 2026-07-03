package com.spendwise.analytics;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Required tests for E7-S1 (docs/testing.md Analytics unit tests: "aggregation correctness"). */
class AnalyticsServiceImplTest {

    private final AnalyticsRepository analyticsRepository = mock(AnalyticsRepository.class);
    private final AnalyticsServiceImpl service = new AnalyticsServiceImpl(analyticsRepository);
    private final UUID userId = UUID.randomUUID();
    private static final OverallTotals ZERO_TOTALS = new OverallTotals(BigDecimal.ZERO, BigDecimal.ZERO);

    @Test
    void summaryThrowsWhenFromOrToIsMissing() {
        assertThrows(InvalidAnalyticsQueryException.class, () -> service.summary(userId, null, Instant.now()));
        assertThrows(InvalidAnalyticsQueryException.class, () -> service.summary(userId, Instant.now(), null));
    }

    @Test
    void summaryThrowsWhenFromIsAfterTo() {
        Instant from = Instant.parse("2026-02-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-01T00:00:00Z");

        assertThrows(InvalidAnalyticsQueryException.class, () -> service.summary(userId, from, to));
    }

    @Test
    void summaryComposesOverallAndCategoryTotalsFromTheRepository() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        OverallTotals overall = new OverallTotals(BigDecimal.valueOf(500), BigDecimal.valueOf(100));
        List<CategoryTotal> categories = List.of(new CategoryTotal(1, "Food / Dine Out", BigDecimal.valueOf(500), BigDecimal.ZERO, 3));
        given(analyticsRepository.overallTotals(userId, from, to)).willReturn(overall);
        given(analyticsRepository.categoryTotals(userId, from, to)).willReturn(categories);

        AnalyticsSummary summary = service.summary(userId, from, to);

        assertThat(summary.overall()).isEqualTo(overall);
        assertThat(summary.categories()).isEqualTo(categories);
    }

    @Test
    void comparisonThrowsForAnInvalidGranularity() {
        assertThrows(InvalidAnalyticsQueryException.class, () -> service.comparison(userId, "decade"));
    }

    @Test
    void comparisonDefaultsToMonthGranularityWhenOmitted() {
        given(analyticsRepository.overallTotals(any(), any(), any())).willReturn(ZERO_TOTALS);
        given(analyticsRepository.categoryTotals(any(), any(), any())).willReturn(List.of());

        AnalyticsComparison comparison = service.comparison(userId, null);

        assertThat(comparison.granularity()).isEqualTo("month");
    }

    @Test
    void comparisonComputesCurrentAndPreviousCalendarMonthBoundaries() {
        given(analyticsRepository.overallTotals(any(), any(), any())).willReturn(ZERO_TOTALS);
        given(analyticsRepository.categoryTotals(any(), any(), any())).willReturn(List.of());

        YearMonth thisMonth = YearMonth.now(ZoneOffset.UTC);
        Instant expectedCurrentFrom = thisMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant expectedCurrentToExclusive = thisMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant expectedPreviousFrom = thisMonth.minusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        service.comparison(userId, "month");

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository, times(2)).overallTotals(eq(userId), fromCaptor.capture(), toCaptor.capture());

        List<Instant> froms = fromCaptor.getAllValues();
        List<Instant> tos = toCaptor.getAllValues();
        // First invocation is the current period, second is the previous period (service.comparison's call order).
        assertThat(froms.get(0)).isEqualTo(expectedCurrentFrom);
        assertThat(tos.get(0)).isEqualTo(expectedCurrentToExclusive.minusMillis(1));
        assertThat(froms.get(1)).isEqualTo(expectedPreviousFrom);
        assertThat(tos.get(1)).isEqualTo(expectedCurrentFrom.minusMillis(1));
    }

    @Test
    void comparisonComputesCurrentAndPreviousIsoWeekBoundaries() {
        given(analyticsRepository.overallTotals(any(), any(), any())).willReturn(ZERO_TOTALS);
        given(analyticsRepository.categoryTotals(any(), any(), any())).willReturn(List.of());

        LocalDate mondayThisWeek = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        Instant expectedCurrentFrom = mondayThisWeek.atStartOfDay(ZoneOffset.UTC).toInstant();

        service.comparison(userId, "week");

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository, times(2)).overallTotals(eq(userId), fromCaptor.capture(), any());
        assertThat(fromCaptor.getAllValues().get(0)).isEqualTo(expectedCurrentFrom);
        assertThat(fromCaptor.getAllValues().get(1)).isEqualTo(mondayThisWeek.minusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void comparisonComputesCurrentAndPreviousCalendarYearBoundaries() {
        given(analyticsRepository.overallTotals(any(), any(), any())).willReturn(ZERO_TOTALS);
        given(analyticsRepository.categoryTotals(any(), any(), any())).willReturn(List.of());

        int thisYear = LocalDate.now(ZoneOffset.UTC).getYear();
        Instant expectedCurrentFrom = LocalDate.of(thisYear, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

        service.comparison(userId, "YEAR");

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(analyticsRepository, times(2)).overallTotals(eq(userId), fromCaptor.capture(), any());
        assertThat(fromCaptor.getAllValues().get(0)).isEqualTo(expectedCurrentFrom);
        assertThat(fromCaptor.getAllValues().get(1)).isEqualTo(LocalDate.of(thisYear - 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void trendsThrowsForAnInvalidGranularity() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T00:00:00Z");

        assertThrows(InvalidAnalyticsQueryException.class, () -> service.trends(userId, "decade", from, to, null));
    }

    @Test
    void trendsThrowsWhenFromOrToIsMissing() {
        assertThrows(InvalidAnalyticsQueryException.class, () -> service.trends(userId, "month", null, Instant.now(), null));
    }

    @Test
    void trendsDelegatesToRepositoryWithNormalizedGranularityAndOptionalCategoryFilter() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-31T00:00:00Z");
        List<TrendBucket> buckets = List.of(new TrendBucket(from, BigDecimal.valueOf(200)));
        given(analyticsRepository.trends(userId, "month", from, to, 4)).willReturn(buckets);

        AnalyticsTrends trends = service.trends(userId, "MONTH", from, to, 4);

        assertThat(trends.granularity()).isEqualTo("month");
        assertThat(trends.buckets()).isEqualTo(buckets);
    }

    @Test
    void exportRowsThrowsWhenFromOrToIsMissing() {
        assertThrows(InvalidAnalyticsQueryException.class, () -> service.exportRows(userId, null, Instant.now()));
    }

    @Test
    void exportRowsDelegatesToRepository() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T00:00:00Z");
        List<AnalyticsExportRow> rows = List.of(new AnalyticsExportRow(
                UUID.randomUUID(), from, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.valueOf(-10), null, "UPI", "DR", "ref1",
                "Swiggy", "HDFC", "swiggy@okhdfc", null, "sms", 7, "Food / Dine Out"));
        given(analyticsRepository.findAllForExport(userId, from, to)).willReturn(rows);

        List<AnalyticsExportRow> result = service.exportRows(userId, from, to);

        assertThat(result).isEqualTo(rows);
    }
}
