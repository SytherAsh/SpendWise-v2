package com.spendwise.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Required tests for E3-S3-T1/T2 (docs/testing.md EMI CRUD unit tests). */
class EmiServiceImplTest {

    private final EmiRepository emiRepository = mock(EmiRepository.class);
    private final EmiServiceImpl service = new EmiServiceImpl(emiRepository);
    private final UUID userId = UUID.randomUUID();

    @Test
    void createManualAlwaysHasNotDetectedFromSmsAndNoSourceTransaction() {
        Emi persisted = new Emi(UUID.randomUUID(), userId, "Home Loan EMI", BigDecimal.valueOf(15000), 5, false, true, null);
        given(emiRepository.insertManual(userId, "Home Loan EMI", BigDecimal.valueOf(15000), 5)).willReturn(persisted);

        Emi result = service.createManual(userId, "Home Loan EMI", BigDecimal.valueOf(15000), 5);

        assertThat(result.detectedFromSms()).isFalse();
        assertThat(result.sourceTransactionId()).isNull();
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void updateThrowsNotFoundForMissingOrForeignEmi() {
        UUID emiId = UUID.randomUUID();
        given(emiRepository.findById(userId, emiId)).willReturn(Optional.empty());

        assertThrows(EmiNotFoundException.class, () -> service.update(userId, emiId, "New label", BigDecimal.TEN, 10));
    }

    @Test
    void updateWritesLabelAmountAndDueDayWhenEmiExists() {
        UUID emiId = UUID.randomUUID();
        given(emiRepository.findById(userId, emiId))
                .willReturn(Optional.of(new Emi(emiId, userId, "Old label", BigDecimal.ONE, 1, false, true, null)));

        service.update(userId, emiId, "New label", BigDecimal.valueOf(2000), 15);

        verify(emiRepository).update(userId, emiId, "New label", BigDecimal.valueOf(2000), 15);
    }

    @Test
    void deactivateThrowsNotFoundForMissingOrForeignEmi() {
        UUID emiId = UUID.randomUUID();
        given(emiRepository.findById(userId, emiId)).willReturn(Optional.empty());

        assertThrows(EmiNotFoundException.class, () -> service.deactivate(userId, emiId));
    }

    @Test
    void deactivateSetsInactiveWithoutDeletingWhenEmiExists() {
        UUID emiId = UUID.randomUUID();
        given(emiRepository.findById(userId, emiId))
                .willReturn(Optional.of(new Emi(emiId, userId, "Label", BigDecimal.TEN, 5, false, true, null)));

        service.deactivate(userId, emiId);

        verify(emiRepository).deactivate(userId, emiId);
    }
}
