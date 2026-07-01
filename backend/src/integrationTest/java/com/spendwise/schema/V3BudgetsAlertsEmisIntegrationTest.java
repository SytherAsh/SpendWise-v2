package com.spendwise.schema;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Required tests for E0-S2-T4: chk_budget_limit_positive, chk_emi_amount_positive,
 * and idx_emis_source_txn rejecting a duplicate source_transaction_id. See
 * db/migration/V3__budgets_alerts_emis.sql.
 */
class V3BudgetsAlertsEmisIntegrationTest extends AbstractSchemaIntegrationTest {

    @Test
    void budgetLimitMustBePositive() {
        UUID userId = insertTestUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO budgets (user_id, category_id, monthly_limit, month, year) VALUES (?, 1, 0, 6,"
                                + " 2026)",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emiAmountMustBePositive() {
        UUID userId = insertTestUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO emis (user_id, label, amount) VALUES (?, 'Test EMI', 0)", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameSourceTransactionCannotBackTwoEmis() {
        UUID userId = insertTestUser();
        UUID txnId = insertTestTransaction(userId, "txn-emi-source");

        jdbcTemplate.update(
                "INSERT INTO emis (user_id, label, amount, source_transaction_id) VALUES (?, 'Netflix', 199, ?)",
                userId,
                txnId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO emis (user_id, label, amount, source_transaction_id) VALUES (?, 'Netflix"
                                + " Duplicate', 199, ?)",
                        userId,
                        txnId))
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
