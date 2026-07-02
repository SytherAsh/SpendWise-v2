package com.spendwise.transaction.dto;

import java.util.List;
import java.util.UUID;

/** Matches docs/api.md Pagination response shape exactly: {data, nextCursor, hasMore}. */
public record TransactionListResponse(List<TransactionResponse> data, UUID nextCursor, boolean hasMore) {}
