package com.spendwise.analytics;

import com.spendwise.analytics.dto.AnalyticsComparisonResponse;
import com.spendwise.analytics.dto.AnalyticsSummaryResponse;
import com.spendwise.analytics.dto.AnalyticsTrendsResponse;
import com.spendwise.analytics.dto.CategoryTotalResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/** docs/api.md `/analytics` — owned by the Analytics module (E7). */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(
            @AuthenticationPrincipal UUID userId, @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return AnalyticsSummaryResponse.from(analyticsService.summary(userId, parseDate(from), parseDate(to)));
    }

    @GetMapping("/categories")
    public List<CategoryTotalResponse> categories(
            @AuthenticationPrincipal UUID userId, @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return analyticsService.categoryBreakdown(userId, parseDate(from), parseDate(to)).stream()
                .map(CategoryTotalResponse::from)
                .toList();
    }

    @GetMapping("/comparison")
    public AnalyticsComparisonResponse comparison(
            @AuthenticationPrincipal UUID userId, @RequestParam(required = false) String granularity) {
        return AnalyticsComparisonResponse.from(analyticsService.comparison(userId, granularity));
    }

    @GetMapping("/trends")
    public AnalyticsTrendsResponse trends(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer category) {
        return AnalyticsTrendsResponse.from(
                analyticsService.trends(userId, granularity, parseDate(from), parseDate(to), category));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal UUID userId, @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        byte[] csv = AnalyticsCsvWriter.write(analyticsService.exportRows(userId, parseDate(from), parseDate(to)));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("spendwise-transactions.csv").build().toString())
                .body(csv);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer financialYear) {
        Instant[] range = resolveExportRange(from, to, financialYear);
        AnalyticsSummary summary = analyticsService.summary(userId, range[0], range[1]);
        byte[] pdf = AnalyticsPdfReportBuilder.build(summary, range[0], range[1]);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("spendwise-report.pdf").build().toString())
                .body(pdf);
    }

    /**
     * Exactly one of (a) {@code from}+{@code to}, or (b) {@code financialYear} (meaning the
     * Indian financial year {@code financialYear}-04-01 to {@code (financialYear+1)}-03-31) must
     * be present.
     */
    private static Instant[] resolveExportRange(String from, String to, Integer financialYear) {
        boolean hasRange = from != null && to != null;
        boolean hasFinancialYear = financialYear != null;
        if (hasRange == hasFinancialYear) {
            throw new InvalidAnalyticsQueryException("provide exactly one of (from and to) or financialYear");
        }
        if (hasFinancialYear) {
            Instant start = LocalDate.of(financialYear, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = LocalDate.of(financialYear + 1, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
            return new Instant[] {start, end};
        }
        return new Instant[] {parseDate(from), parseDate(to)};
    }

    /** Accepts both a full ISO-8601 instant and a date-only ISO-8601 string (docs/api.md "ISO 8601 dates"). */
    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }
}
