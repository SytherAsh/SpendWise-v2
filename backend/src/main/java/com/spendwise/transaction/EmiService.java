package com.spendwise.transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for {@code /emis}, owned by the Transaction module (docs/api.md "/emis"
 * module ownership note). No cross-module consumer yet — Epic 6 (recurring detection) is the
 * anticipated future caller of a source-transaction-linked insert path, not built here.
 */
public interface EmiService {

    /** Manual creation (E3-S3-T1) — always {@code detectedFromSms = false}, {@code sourceTransactionId = null}. */
    Emi createManual(UUID userId, String label, BigDecimal amount, Integer dueDay);

    List<Emi> listActive(UUID userId);

    /** @throws EmiNotFoundException if absent or owned by a different user */
    void update(UUID userId, UUID emiId, String label, BigDecimal amount, Integer dueDay);

    /** Sets {@code is_active = false}; never hard-deletes (E3-S3-T2 DoD). */
    void deactivate(UUID userId, UUID emiId);
}
