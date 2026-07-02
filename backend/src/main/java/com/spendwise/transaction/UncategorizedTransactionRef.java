package com.spendwise.transaction;

import java.util.UUID;

/**
 * A cross-user reference (E4-S3-T3) — deliberately carries only the two ids needed to re-scope a
 * normal per-user RLS query, never a full row, since it's produced by a query that bypasses RLS
 * (see {@link TransactionRepository#findAllUncategorized}).
 */
public record UncategorizedTransactionRef(UUID userId, UUID transactionId) {}
