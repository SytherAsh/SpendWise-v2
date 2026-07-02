package com.spendwise.transaction;

import java.util.List;
import java.util.UUID;

/** Service-layer cursor page result; {@link com.spendwise.transaction.dto.TransactionListResponse} is its wire-shape counterpart. */
public record TransactionPage(List<Transaction> data, UUID nextCursor, boolean hasMore) {}
