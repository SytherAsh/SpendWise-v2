package com.spendwise.schema;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Required tests for E0-S2-T3: seed data matches the 12-category table (10
 * seeded in V2, Medical and Fees & Debt added in V7), the DR/CR consistency
 * check, and the transaction dedup unique index. See
 * db/migration/V2__transactions_and_categories.sql,
 * db/migration/V7__add_medical_and_fees_categories.sql, and docs/database.md.
 */
class V2TransactionsAndCategoriesIntegrationTest extends AbstractSchemaIntegrationTest {

    @Test
    void categoriesSeedMatchesSpec() {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList("SELECT id, name, icon FROM categories ORDER BY id");

        assertThat(rows).hasSize(12);
        assertThat(rows.get(0)).containsEntry("name", "Shopping").containsEntry("icon", "shopping_bag");
        assertThat(rows.get(6)).containsEntry("name", "Food / Dine Out").containsEntry("icon", "restaurant");
        assertThat(rows.get(9)).containsEntry("name", "Transfers").containsEntry("icon", "swap_horiz");
        assertThat(rows.get(10)).containsEntry("name", "Medical").containsEntry("icon", "local_hospital");
        assertThat(rows.get(11)).containsEntry("name", "Fees & Debt").containsEntry("icon", "request_quote");
    }

    @Test
    void debitTransactionMustBeNegativeAmountWithZeroCredit() {
        UUID userId = insertTestUser();

        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, transaction_date, debit, credit, amount, dr_cr_indicator,"
                        + " transaction_id, source) VALUES (?, NOW(), 100, 0, -100, 'DR', 'txn-dr-1',"
                        + " 'sms'::transaction_source)",
                userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO transactions (user_id, transaction_date, debit, credit, amount,"
                                + " dr_cr_indicator, transaction_id, source) VALUES (?, NOW(), 100, 0, 100, 'DR',"
                                + " 'txn-dr-bad', 'sms'::transaction_source)",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void creditTransactionMustBePositiveAmountWithZeroDebit() {
        UUID userId = insertTestUser();

        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, transaction_date, debit, credit, amount, dr_cr_indicator,"
                        + " transaction_id, source) VALUES (?, NOW(), 0, 200, 200, 'CR', 'txn-cr-1',"
                        + " 'sms'::transaction_source)",
                userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO transactions (user_id, transaction_date, debit, credit, amount,"
                                + " dr_cr_indicator, transaction_id, source) VALUES (?, NOW(), 0, 200, -200, 'CR',"
                                + " 'txn-cr-bad', 'sms'::transaction_source)",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateTransactionIdForSameUserIsRejected() {
        UUID userId = insertTestUser();

        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, transaction_date, debit, credit, amount, dr_cr_indicator,"
                        + " transaction_id, source) VALUES (?, NOW(), 50, 0, -50, 'DR', 'txn-dup',"
                        + " 'sms'::transaction_source)",
                userId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO transactions (user_id, transaction_date, debit, credit, amount,"
                                + " dr_cr_indicator, transaction_id, source) VALUES (?, NOW(), 50, 0, -50, 'DR',"
                                + " 'txn-dup', 'sms'::transaction_source)",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertTestUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, ?)", userId, userId.toString());
        return userId;
    }
}
