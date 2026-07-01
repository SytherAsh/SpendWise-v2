package com.spendwise.schema;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Required test for E0-S2-T2: chk_user_identifier rejects a user row with
 * both phone and google_id null. See db/migration/V1__identity_and_session_tables.sql.
 */
class V1IdentityAndSessionTablesIntegrationTest extends AbstractSchemaIntegrationTest {

    @Test
    void usersRequiresPhoneOrGoogleId() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO users (phone, google_id) VALUES (NULL, NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
