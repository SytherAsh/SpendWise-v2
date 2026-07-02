package com.spendwise.transaction.dto;

import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level guarantee for E3-S1-T3: no {@code TransactionResponse} field can ever carry
 * {@code sms_raw_text}, because the type doesn't have one — a structural check that fails loudly
 * if a future edit reintroduces it, standing in for "the build fails if a new endpoint
 * accidentally serializes the entity directly" until a GET endpoint exists to black-box test
 * against (added in E3-S2-T1/T2, whose controllers are this DTO's first real consumers).
 */
class TransactionResponseTest {

    @Test
    void hasNoFieldRelatedToRawSmsText() {
        RecordComponent[] components = TransactionResponse.class.getRecordComponents();
        boolean anyRawTextField = Arrays.stream(components)
                .map(RecordComponent::getName)
                .anyMatch(name -> name.toLowerCase().contains("raw"));

        assertThat(anyRawTextField).isFalse();
    }

    @Test
    void fromMapsEveryFieldExceptRawText() {
        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2025-06-15T14:32:00Z"),
                BigDecimal.valueOf(350),
                BigDecimal.ZERO,
                BigDecimal.valueOf(-350),
                BigDecimal.valueOf(1200),
                "UPI",
                "DR",
                "txn_abc123",
                "Swiggy",
                "ICICI",
                "swiggy@okicici",
                "lunch",
                TransactionSource.SMS,
                Instant.parse("2025-06-15T14:33:00Z"),
                7,
                0.92f,
                "ml");

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.id()).isEqualTo(transaction.id());
        assertThat(response.transactionId()).isEqualTo("txn_abc123");
        assertThat(response.source()).isEqualTo("sms");
        assertThat(response.categoryId()).isEqualTo(7);
        assertThat(response.assignedBy()).isEqualTo("ml");
    }
}
