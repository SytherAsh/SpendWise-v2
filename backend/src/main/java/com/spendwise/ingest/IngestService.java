package com.spendwise.ingest;

import com.spendwise.ingest.dto.IngestBatchResponse;
import com.spendwise.ingest.dto.IngestItemResult;
import com.spendwise.ingest.dto.IngestTransactionItem;
import com.spendwise.transaction.NewTransactionData;
import com.spendwise.transaction.TransactionInsertOutcome;
import com.spendwise.transaction.TransactionService;
import com.spendwise.transaction.TransactionSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Batch persistence with per-item dedup outcomes (E3-S1-T2). Calls {@link TransactionService}
 * only through its injected interface (docs/architecture.md — Ingest may call Transaction for
 * persistence only); never touches {@link com.spendwise.transaction.TransactionRepository} or
 * any other Transaction-module internal directly.
 */
@Service
public class IngestService {

    private final TransactionService transactionService;

    public IngestService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public IngestOutcome ingestBatch(UUID userId, List<IngestTransactionItem> items) {
        List<IngestItemResult> results = new ArrayList<>();
        for (IngestTransactionItem item : items) {
            int status;
            try {
                TransactionInsertOutcome outcome = transactionService.persistFromIngest(userId, toNewTransactionData(item));
                status = outcome == TransactionInsertOutcome.CREATED ? HttpStatus.CREATED.value() : HttpStatus.CONFLICT.value();
            } catch (RuntimeException e) {
                // A single bad item (e.g. a DR/CR-consistency constraint violation from a
                // malformed client payload) must not abort the rest of the batch (E3-S1-T2 DoD).
                status = HttpStatus.INTERNAL_SERVER_ERROR.value();
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
