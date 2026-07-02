package com.spendwise.transaction;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionCategoryRepository transactionCategoryRepository;
    private final MlCorrectionRepository mlCorrectionRepository;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            CategoryRepository categoryRepository,
            TransactionCategoryRepository transactionCategoryRepository,
            MlCorrectionRepository mlCorrectionRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.transactionCategoryRepository = transactionCategoryRepository;
        this.mlCorrectionRepository = mlCorrectionRepository;
    }

    @Override
    @Transactional
    public Optional<Transaction> persistFromIngest(UUID userId, NewTransactionData data) {
        // Secondary dedup (docs/database.md) — only meaningful when upi_id is present; runs
        // before the insert attempt so a secondary-key duplicate never even reaches the DB.
        if (data.upiId() != null
                && transactionRepository.existsBySecondaryKey(userId, data.upiId(), data.amount(), data.transactionDate())) {
            return Optional.empty();
        }
        try {
            // source is always forced to SMS here regardless of the request body's "source"
            // field — /ingest/transactions is the Android-app-only path (E3-S1-T2 DoD).
            NewTransactionData ingestData = withSource(data, TransactionSource.SMS);
            return Optional.of(transactionRepository.insert(userId, ingestData));
        } catch (DuplicateKeyException e) {
            // Primary dedup — idx_transactions_unique_dedup is the authoritative guard.
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Transaction createManual(UUID userId, NewTransactionData data) {
        // transaction_id is meaningless for manual entries (no bank reference to key off of) —
        // always server-generated, never accepted from the client.
        NewTransactionData manualData = new NewTransactionData(
                data.transactionDate(),
                data.debit(),
                data.credit(),
                data.amount(),
                data.balance(),
                data.transactionMode(),
                data.drCrIndicator(),
                UUID.randomUUID().toString(),
                data.recipientName(),
                data.bank(),
                data.upiId(),
                data.note(),
                TransactionSource.MANUAL);
        return transactionRepository.insert(userId, manualData);
    }

    @Override
    @Transactional
    public TransactionPage list(UUID userId, int limit, UUID cursor, Integer categoryId, Instant from, Instant to) {
        Instant cursorDate = cursor == null ? null : transactionRepository.findTransactionDate(userId, cursor).orElse(null);
        UUID effectiveCursorId = cursorDate == null ? null : cursor;
        List<Transaction> rows =
                transactionRepository.listPage(userId, categoryId, from, to, cursorDate, effectiveCursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Transaction> page = hasMore ? rows.subList(0, limit) : rows;
        UUID nextCursor = hasMore ? page.get(page.size() - 1).id() : null;
        return new TransactionPage(page, nextCursor, hasMore);
    }

    @Override
    @Transactional
    public Transaction getById(UUID userId, UUID transactionId) {
        return transactionRepository.findById(userId, transactionId).orElseThrow(TransactionNotFoundException::new);
    }

    @Override
    @Transactional
    public void correctCategory(UUID userId, UUID transactionId, int categoryId) {
        transactionRepository.findById(userId, transactionId).orElseThrow(TransactionNotFoundException::new);
        if (!categoryRepository.existsById(categoryId)) {
            throw new InvalidCategoryException(categoryId);
        }
        Integer oldCategoryId = transactionCategoryRepository.findCategoryId(userId, transactionId).orElse(null);
        if (oldCategoryId != null && oldCategoryId == categoryId) {
            return; // no-op correction — chk_correction_different_category would reject old = new anyway
        }
        transactionCategoryRepository.upsertUserAssignment(userId, transactionId, categoryId);
        mlCorrectionRepository.insert(userId, transactionId, oldCategoryId, categoryId);
    }

    @Override
    @Transactional
    public void assignMlCategory(UUID userId, UUID transactionId, int categoryId, double confidence) {
        transactionCategoryRepository.upsertMlAssignment(userId, transactionId, categoryId, confidence);
    }

    @Override
    @Transactional
    public boolean isCategorized(UUID userId, UUID transactionId) {
        return transactionCategoryRepository.hasAssignment(userId, transactionId);
    }

    @Override
    public List<UncategorizedTransactionRef> findAllUncategorized(int limit) {
        // No @Transactional / RlsSession here — this reads via the separate spendwise_jobs
        // DataSource (BYPASSRLS), which the primary DataSource's transaction manager doesn't
        // span, and a single SELECT needs no explicit transaction anyway.
        return transactionRepository.findAllUncategorized(limit);
    }

    @Override
    public List<MlCorrectionRecord> findAllCorrections() {
        return mlCorrectionRepository.findAllCorrections();
    }

    private static NewTransactionData withSource(NewTransactionData data, TransactionSource source) {
        return new NewTransactionData(
                data.transactionDate(),
                data.debit(),
                data.credit(),
                data.amount(),
                data.balance(),
                data.transactionMode(),
                data.drCrIndicator(),
                data.transactionId(),
                data.recipientName(),
                data.bank(),
                data.upiId(),
                data.note(),
                source);
    }
}
