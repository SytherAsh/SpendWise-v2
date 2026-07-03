package com.spendwise.recommendations;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Required Tests for E8-S2-T1 (docs/architecture.md's idempotency description):
 * a genuine new threshold-crossing produces exactly one new active recommendation; running the
 * job again with no new crossing produces no duplicate. Requires Docker — run via {@code
 * ./gradlew integrationTest}. Mirrors {@code RecurringPaymentEvaluatorIntegrationTest}'s approach
 * of autowiring the scheduled-job class directly and seeding transactions via raw SQL.
 *
 * <p>The {@code spendwise_jobs} role (db-init/02-jobs-role.sql in real deployments) doesn't exist
 * in a bare Testcontainers Postgres image, so {@link #createJobsRole()} creates it directly,
 * mirroring {@code CategorizationJobsIntegrationTest}/{@code RecurringPaymentEvaluatorIntegrationTest}.
 */
@Testcontainers
@SpringBootTest
class RecommendationGeneratorJobIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void createJobsRole() throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE spendwise_jobs WITH LOGIN PASSWORD 'spendwise_jobs_password' BYPASSRLS");
            statement.execute("GRANT " + POSTGRES.getUsername() + " TO spendwise_jobs");
        }
    }

    @Autowired
    private RecommendationGeneratorJob job;

    @Autowired
    private RecommendationsService recommendationsService;

    @Test
    void aGenuineNewCrossingProducesOneRecommendationAndARerunProducesNoDuplicate() throws Exception {
        UUID userId = UUID.randomUUID();
        insertUser(userId);
        // Category 7 (Food / Dine Out): previous month 2600 (>= 200 baseline), current month
        // 3200 -- a ~23% increase, above the 20% threshold.
        insertCategorizedTransaction(userId, 7, "2600.00", "NOW() - INTERVAL '1 month'");
        insertCategorizedTransaction(userId, 7, "3200.00", "NOW()");

        job.run();

        List<Recommendation> afterFirstRun = recommendationsService.listActive(userId);
        assertThat(afterFirstRun).hasSize(1);
        assertThat(afterFirstRun.get(0).categoryId()).isEqualTo(7);

        job.run();

        List<Recommendation> afterSecondRun = recommendationsService.listActive(userId);
        assertThat(afterSecondRun).hasSize(1);
        assertThat(afterSecondRun.get(0).id()).isEqualTo(afterFirstRun.get(0).id());
    }

    private void insertUser(UUID userId) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (id, phone, created_at) VALUES ('" + userId + "', '+91900000" + Math.abs(userId.hashCode() % 10000)
                    + "', NOW())");
        }
    }

    private void insertCategorizedTransaction(UUID userId, int categoryId, String amount, String transactionDateExpr) throws Exception {
        UUID transactionId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, "
                    + "transaction_mode, dr_cr_indicator, transaction_id, recipient_name, bank, upi_id, note, source, parsed_at) "
                    + "VALUES ('" + transactionId + "', '" + userId + "', " + transactionDateExpr + ", " + amount + ", 0, -" + amount + ", "
                    + "'UPI', 'DR', '" + UUID.randomUUID() + "', 'Merchant', 'ICICI', 'merchant@upi', NULL, 'sms', NOW())");
            statement.execute("INSERT INTO transaction_categories (transaction_id, category_id, confidence_score, assigned_by) "
                    + "VALUES ('" + transactionId + "', " + categoryId + ", 0.9, 'ml')");
        }
    }
}
