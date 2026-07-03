package com.spendwise.analytics;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.spendwise.auth.FirebaseAuthTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E7-S1/S2 (docs/testing.md Analytics unit tests +
 * this epic's own integration DoDs — hand-verifiable totals against a fixed fixture). Requires
 * Docker — run via {@code ./gradlew integrationTest}.
 *
 * <p>Fixture data for the date-ranged endpoints (summary/categories/trends/export) lives in
 * distinct, dedicated calendar months so tests never contend with each other (same accommodation
 * {@code BudgetControllerIntegrationTest} makes with per-test category ids, since every test
 * method here shares one real user for the lifetime of this class's single Testcontainers
 * instance). {@code /analytics/comparison} is the exception — it's anchored to "today," so its
 * fixture is seeded relative to {@link YearMonth#now()} instead of a fixed date.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FirebaseAuthTestConfig.class)
class AnalyticsControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @Test
    void summaryMatchesHandComputedTotalsForAFixedDateRange() {
        HttpHeaders headers = authHeaders();
        // Food (7): 2 debits = 300; Shopping (1): 1 debit = 150 + 1 credit (refund) = 50 income.
        categorize(headers, createManual(headers, "2025-01-05T10:00:00Z", -200.0), 7);
        categorize(headers, createManual(headers, "2025-01-10T10:00:00Z", -100.0), 7);
        categorize(headers, createManual(headers, "2025-01-15T10:00:00Z", -150.0), 1);
        categorize(headers, createManual(headers, "2025-01-20T10:00:00Z", 50.0), 1);
        // Outside the queried range — must not be counted.
        categorize(headers, createManual(headers, "2025-02-01T10:00:00Z", -999.0), 7);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/analytics/summary?from=2025-01-01&to=2025-01-31", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(numberOf(response.getBody().get("totalSpend"))).isEqualByComparingTo("450");
        assertThat(numberOf(response.getBody().get("totalIncome"))).isEqualByComparingTo("50");
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.getBody().get("categories");
        assertThat(categories).filteredOn(c -> c.get("categoryId").equals(7)).singleElement().satisfies(c -> {
            assertThat(numberOf(c.get("totalSpend"))).isEqualByComparingTo("300");
            assertThat(c.get("transactionCount")).isEqualTo(2);
        });
        assertThat(categories).filteredOn(c -> c.get("categoryId").equals(1)).singleElement().satisfies(c -> {
            assertThat(numberOf(c.get("totalSpend"))).isEqualByComparingTo("150");
            assertThat(numberOf(c.get("totalIncome"))).isEqualByComparingTo("50");
            assertThat(c.get("transactionCount")).isEqualTo(2);
        });
    }

    @Test
    void categoriesReturnsPerCategoryTotalsAndTransactionCounts() {
        HttpHeaders headers = authHeaders();
        categorize(headers, createManual(headers, "2025-02-05T10:00:00Z", -40.0), 4);
        categorize(headers, createManual(headers, "2025-02-06T10:00:00Z", -60.0), 4);
        categorize(headers, createManual(headers, "2025-02-07T10:00:00Z", -25.0), 3);

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/analytics/categories?from=2025-02-01&to=2025-02-28", HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> categories = response.getBody();
        assertThat(categories).filteredOn(c -> ((Map) c).get("categoryId").equals(4)).singleElement().satisfies(c -> {
            assertThat(numberOf(((Map) c).get("totalSpend"))).isEqualByComparingTo("100");
            assertThat(((Map) c).get("transactionCount")).isEqualTo(2);
        });
        assertThat(categories).filteredOn(c -> ((Map) c).get("categoryId").equals(3)).singleElement().satisfies(c -> {
            assertThat(numberOf(((Map) c).get("totalSpend"))).isEqualByComparingTo("25");
            assertThat(((Map) c).get("transactionCount")).isEqualTo(1);
        });
    }

    @Test
    void trendsReturnsOneBucketPerMonthWithCorrectTotals() {
        HttpHeaders headers = authHeaders();
        categorize(headers, createManual(headers, "2025-03-10T10:00:00Z", -70.0), 2);
        categorize(headers, createManual(headers, "2025-03-20T10:00:00Z", -30.0), 2);
        categorize(headers, createManual(headers, "2025-04-15T10:00:00Z", -55.0), 2);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/analytics/trends?granularity=month&from=2025-03-01&to=2025-04-30",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) response.getBody().get("buckets");
        assertThat(buckets).hasSize(2);
        assertThat(numberOf(buckets.get(0).get("totalSpend"))).isEqualByComparingTo("100");
        assertThat(numberOf(buckets.get(1).get("totalSpend"))).isEqualByComparingTo("55");
    }

    @Test
    void comparisonForMonthGranularityMatchesCurrentVsPreviousMonth() {
        HttpHeaders headers = authHeaders();
        YearMonth thisMonth = YearMonth.now(java.time.ZoneOffset.UTC);
        YearMonth lastMonth = thisMonth.minusMonths(1);
        createManual(headers, thisMonth.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString(), -120.0);
        createManual(headers, lastMonth.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString(), -80.0);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/analytics/comparison?granularity=month", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> current = (Map<String, Object>) response.getBody().get("current");
        Map<String, Object> previous = (Map<String, Object>) response.getBody().get("previous");
        assertThat(numberOf(current.get("totalSpend"))).isEqualByComparingTo("120");
        assertThat(numberOf(previous.get("totalSpend"))).isEqualByComparingTo("80");
    }

    @Test
    void exportCsvContainsExpectedRowsAndNeverIncludesSmsRawText() {
        HttpHeaders headers = authHeaders();
        createManual(headers, "2025-05-05T10:00:00Z", -42.5);
        createManual(headers, "2025-05-06T10:00:00Z", -17.0);

        HttpHeaders csvHeaders = authHeaders();
        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl() + "/analytics/export/csv?from=2025-05-01&to=2025-05-31", HttpMethod.GET, new HttpEntity<>(csvHeaders), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.strip().split("\r\n");
        assertThat(lines[0]).doesNotContain("sms_raw_text");
        // header + 2 data rows
        assertThat(lines).hasSize(3);
        assertThat(csv).doesNotContain("sms_raw_text");
    }

    @Test
    void exportPdfProducesAValidPdfMatchingSummaryTotalsForACustomRangeAndForAFinancialYear() throws Exception {
        HttpHeaders headers = authHeaders();
        createManual(headers, "2025-06-05T10:00:00Z", -300.0);

        ResponseEntity<byte[]> customRange = restTemplate.exchange(
                baseUrl() + "/analytics/export/pdf?from=2025-06-01&to=2025-06-30", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(customRange.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(customRange.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        PdfReader reader = new PdfReader(customRange.getBody());
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains("300");

        ResponseEntity<byte[]> financialYear = restTemplate.exchange(
                baseUrl() + "/analytics/export/pdf?financialYear=2025", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(financialYear.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(financialYear.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void exportPdfRejectsBothOrNeitherOfRangeAndFinancialYear() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> neither = restTemplate.exchange(
                baseUrl() + "/analytics/export/pdf", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(neither.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> both = restTemplate.exchange(
                baseUrl() + "/analytics/export/pdf?from=2025-01-01&to=2025-01-31&financialYear=2025",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);
        assertThat(both.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void summaryRejectsAMissingDateRangeWith400() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> response =
                restTemplate.exchange(baseUrl() + "/analytics/summary", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String createManual(HttpHeaders headers, String transactionDate, double amount) {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/transactions",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("transactionDate", transactionDate, "amount", amount), headers),
                Map.class);
        return (String) response.getBody().get("id");
    }

    private void categorize(HttpHeaders headers, String transactionId, int categoryId) {
        restTemplate.exchange(
                baseUrl() + "/transactions/" + transactionId + "/category",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("category_id", categoryId), headers),
                Map.class);
    }

    private static java.math.BigDecimal numberOf(Object value) {
        return new java.math.BigDecimal(value.toString());
    }

    private HttpHeaders authHeaders() {
        ResponseEntity<Map> verify = restTemplate.postForEntity(
                baseUrl() + "/auth/otp/verify",
                Map.of("phone", "+912200000099", "idToken", FirebaseAuthTestConfig.VALID_OTP_TOKEN_PHONE_A),
                Map.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) verify.getBody().get("accessToken"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
