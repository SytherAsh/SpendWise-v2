package com.spendwise.schema;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Required tests for E0-S2-T5: chk_correction_different_category (including
 * the null-old-category case, which must be allowed) and the recommendations
 * partial unique index. See db/migration/V4__ml_admin_chatbot_tables.sql.
 */
class V4MlAdminChatbotTablesIntegrationTest extends AbstractSchemaIntegrationTest {

    @Test
    void correctionWithNullOldCategoryIsAllowed() {
        UUID userId = insertTestUser();
        UUID txnId = insertTestTransaction(userId, "txn-correction-null-old");

        assertThatCode(() -> jdbcTemplate.update(
                        "INSERT INTO ml_corrections (transaction_id, old_category_id, new_category_id) VALUES (?,"
                                + " NULL, 5)",
                        txnId))
                .doesNotThrowAnyException();
    }

    @Test
    void correctionWithSameOldAndNewCategoryIsRejected() {
        UUID userId = insertTestUser();
        UUID txnId = insertTestTransaction(userId, "txn-correction-same");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO ml_corrections (transaction_id, old_category_id, new_category_id) VALUES (?,"
                                + " 5, 5)",
                        txnId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondActiveRecommendationForSameUserAndCategoryIsRejected() {
        UUID userId = insertTestUser();

        jdbcTemplate.update(
                "INSERT INTO recommendations (user_id, category_id, text, priority) VALUES (?, 7, 'Spend insight"
                        + " A', 'low')",
                userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO recommendations (user_id, category_id, text, priority) VALUES (?, 7, 'Spend"
                                + " insight B', 'low')",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertTestUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, ?)", userId, userId.toString());
        return userId;
    }

    private UUID insertTestTransaction(UUID userId, String transactionId) {
        UUID txnId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, transaction_date, debit, credit, amount, dr_cr_indicator,"
                        + " transaction_id, source) VALUES (?, ?, NOW(), 199, 0, -199, 'DR', ?,"
                        + " 'sms'::transaction_source)",
                txnId,
                userId,
                transactionId);
        return txnId;
    }
}
