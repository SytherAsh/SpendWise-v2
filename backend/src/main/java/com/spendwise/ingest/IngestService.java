package com.spendwise.ingest;

import com.spendwise.categorization.CategorizationService;
import com.spendwise.ingest.dto.IngestBatchResponse;
import com.spendwise.ingest.dto.IngestItemResult;
import com.spendwise.ingest.dto.IngestTransactionItem;
import com.spendwise.transaction.NewTransactionData;
import com.spendwise.transaction.Transaction;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.TransactionSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Batch persistence with per-item dedup outcomes (E3-S1-T2), then an immediate categorization
 * trigger per created item (E4-S3-T2). Calls {@link TransactionService} and {@link
 * CategorizationService} only through their injected interfaces (docs/architecture.md — Ingest
 * may call Transaction for persistence and Categorization to trigger); never touches either
 * module's repositories or {@code MlClient} directly.
 */
@Service
public class IngestService {

    private final TransactionService transactionService;
    private final CategorizationService categorizationService;

    public IngestService(TransactionService transactionService, CategorizationService categorizationService) {
        this.transactionService = transactionService;
        this.categorizationService = categorizationService;
    }

    public IngestOutcome ingestBatch(UUID userId, List<IngestTransactionItem> items) {
        List<IngestItemResult> results = new ArrayList<>();
        for (IngestTransactionItem item : items) {
            int status;
            Optional<Transaction> created;
            try {
                created = transactionService.persistFromIngest(userId, toNewTransactionData(item));
            } catch (RuntimeException e) {
                // A single bad item (e.g. a DR/CR-consistency constraint violation from a
                // malformed client payload) must not abort the rest of the batch (E3-S1-T2 DoD).
                created = null;
            }

            if (created == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            } else if (created.isPresent()) {
                status = HttpStatus.CREATED.value();
                // "Immediately after a transaction is persisted" (E4-S3-T2 objective), in its own
                // try/catch: CategorizationService's own contract is to never throw (E4-S3-T1),
                // but isolating the call here too means a failed/low-confidence /predict call can
                // never downgrade an already-successful persist to a 500 (E4-S3-T1 DoD — "rather
                // than crashing the ingest flow").
                try {
                    categorizationService.categorize(userId, created.get().id());
                } catch (RuntimeException e) {
                    // Persist already succeeded; leave the item's status as 201. The transaction
                    // stays uncategorized for E4-S3-T3's retry job.
                }
            } else {
                status = HttpStatus.CONFLICT.value();
            }
            results.add(new IngestItemResult(item.transactionId(), status));
        }

        boolean allDuplicates = !results.isEmpty() && results.stream().allMatch(r -> r.status() == HttpStatus.CONFLICT.value());
        HttpStatus overallStatus = allDuplicates ? HttpStatus.CONFLICT : HttpStatus.OK;
        return new IngestOutcome(new IngestBatchResponse(results), overallStatus);
    }

    private static NewTransactionData toNewTransactionData(IngestTransactionItem item) {
        return new NewTransactionData(
                item.transactionDate(),
                item.debit(),
                item.credit(),
                item.amount(),
                item.balance(),
                item.transactionMode(),
                item.drCrIndicator(),
                item.transactionId(),
                item.recipientName(),
                item.bank(),
                item.upiId(),
                item.note(),
                TransactionSource.SMS);
    }
}
