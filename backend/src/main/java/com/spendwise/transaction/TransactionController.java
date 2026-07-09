package com.spendwise.transaction;

import com.spendwise.transaction.dto.CategoryCorrectionResponse;
import com.spendwise.transaction.dto.CorrectCategoryRequest;
import com.spendwise.transaction.dto.CreateTransactionRequest;
import com.spendwise.transaction.dto.TransactionListResponse;
import com.spendwise.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public TransactionListResponse list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        CategoryFilter filter = parseCategoryFilter(category);
        Boolean creditOnly = parseDirection(direction);
        int effectiveLimit = limit != null ? limit : DEFAULT_PAGE_SIZE;
        if (isAmountDescSort(sort)) {
            if (cursor != null) {
                throw new InvalidSortException("cursor pagination is not supported with sort=amount_desc");
            }
            List<TransactionResponse> data = transactionService
                    .topByAmount(userId, filter.categoryId(), filter.uncategorizedOnly(), parseDate(from), parseDate(to), effectiveLimit)
                    .stream()
                    .map(TransactionResponse::from)
                    .toList();
            return new TransactionListResponse(data, null, false);
        }
        if (sort != null && !sort.isBlank() && !sort.equalsIgnoreCase("date_desc")) {
            throw new InvalidSortException("sort must be \"date_desc\" or \"amount_desc\", got: " + sort);
        }
        TransactionPage page = transactionService.list(
                userId,
                effectiveLimit,
                cursor,
                filter.categoryId(),
                filter.uncategorizedOnly(),
                parseDate(from),
                parseDate(to),
                creditOnly);
        List<TransactionResponse> data = page.data().stream().map(TransactionResponse::from).toList();
        return new TransactionListResponse(data, page.nextCursor(), page.hasMore());
    }

    /** {@code direction} is either {@code "credit"}, {@code "debit"}, or absent (docs/api.md "direction"). */
    private static Boolean parseDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        if (direction.equalsIgnoreCase("credit")) {
            return true;
        }
        if (direction.equalsIgnoreCase("debit")) {
            return false;
        }
        throw new InvalidDirectionException(direction);
    }

    private static boolean isAmountDescSort(String sort) {
        return sort != null && sort.equalsIgnoreCase("amount_desc");
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return TransactionResponse.from(transactionService.getById(userId, id));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateTransactionRequest request) {
        Transaction created = transactionService.createManual(userId, toNewTransactionData(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(created));
    }

    @PutMapping("/{id}/category")
    public CategoryCorrectionResponse correctCategory(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id, @Valid @RequestBody CorrectCategoryRequest request) {
        transactionService.correctCategory(userId, id, request.categoryId());
        return new CategoryCorrectionResponse(id, request.categoryId());
    }

    private static NewTransactionData toNewTransactionData(CreateTransactionRequest request) {
        BigDecimal amount = request.amount();
        boolean isDebit = amount.signum() < 0;
        BigDecimal debit = isDebit ? amount.abs() : BigDecimal.ZERO;
        BigDecimal credit = isDebit ? BigDecimal.ZERO : amount;
        return new NewTransactionData(
                request.transactionDate(),
                debit,
                credit,
                amount,
                request.balance(),
                request.transactionMode(),
                isDebit ? "DR" : "CR",
                null, // server-generated by TransactionServiceImpl.createManual
                request.recipientName(),
                request.bank(),
                request.upiId(),
                request.note(),
                TransactionSource.MANUAL);
    }

    /** Accepts both a full ISO-8601 instant and a date-only ISO-8601 string (docs/api.md "ISO 8601 dates"). */
    private static Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    private record CategoryFilter(Integer categoryId, boolean uncategorizedOnly) {}

    /** {@code category} is either a numeric category id, the literal {@code "uncategorized"}, or absent. */
    private static CategoryFilter parseCategoryFilter(String category) {
        if (category == null || category.isBlank()) {
            return new CategoryFilter(null, false);
        }
        if (category.equalsIgnoreCase("uncategorized")) {
            return new CategoryFilter(null, true);
        }
        try {
            return new CategoryFilter(Integer.parseInt(category), false);
        } catch (NumberFormatException e) {
            throw new InvalidCategoryFilterException(category);
        }
    }
}
