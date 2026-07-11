package com.spendwise.transaction;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class EmiServiceImpl implements EmiService {

    private final EmiRepository emiRepository;

    public EmiServiceImpl(EmiRepository emiRepository) {
        this.emiRepository = emiRepository;
    }

    @Override
    @Transactional
    public Emi createManual(UUID userId, String label, BigDecimal amount, Integer dueDay) {
        return emiRepository.insertManual(userId, label, amount, dueDay);
    }

    @Override
    @Transactional
    public List<Emi> listActive(UUID userId) {
        return emiRepository.findActiveForUser(userId);
    }

    @Override
    @Transactional
    public void update(UUID userId, UUID emiId, String label, BigDecimal amount, Integer dueDay) {
        emiRepository.findById(userId, emiId).orElseThrow(EmiNotFoundException::new);
        emiRepository.update(userId, emiId, label, amount, dueDay);
    }

    @Override
    @Transactional
    public void deactivate(UUID userId, UUID emiId) {
        emiRepository.findById(userId, emiId).orElseThrow(EmiNotFoundException::new);
        emiRepository.deactivate(userId, emiId);
    }

    @Override
    public Set<UUID> findAllActiveSourceTransactionIds() {
        // No @Transactional / RlsSession here — reads via the spendwise_jobs DataSource
        // (BYPASSRLS), same reasoning as TransactionServiceImpl#findAllUncategorized.
        return emiRepository.findAllActiveSourceTransactionIds();
    }

    @Override
    @Transactional
    public Emi createFromDetection(
            UUID userId, String label, BigDecimal amount, UUID sourceTransactionId, String cadence, Double confidenceScore) {
        Optional<Emi> existing = emiRepository.findBySourceTransactionId(userId, sourceTransactionId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return emiRepository.insertFromDetection(userId, label, amount, sourceTransactionId, cadence, confidenceScore);
        } catch (DuplicateKeyException e) {
            // Race between the check above and this insert (e.g. a double-click confirming the
            // same alert) — idx_emis_source_txn is the authoritative guard; fall back to the row
            // it just protected rather than surfacing a 500 (E6-S2-T2 DoD).
            return emiRepository.findBySourceTransactionId(userId, sourceTransactionId).orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
    public Optional<Emi> findBySourceTransaction(UUID userId, UUID sourceTransactionId) {
        return emiRepository.findBySourceTransactionId(userId, sourceTransactionId);
    }
}
