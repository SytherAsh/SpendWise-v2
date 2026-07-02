package com.spendwise.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
}
