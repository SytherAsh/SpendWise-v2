package com.spendwise.ingest.dto;

/**
 * Per-item ingest outcome. Response shape (camelCase, per docs/api.md's own convention for every
 * other response body) is not specified by docs/api.md for this endpoint — only the request
 * schema is frozen there. Built independent of the Android Sync module's (Epic 2) own guess at
 * this shape, per this epic's instruction to ground the response in docs/api.md's conventions
 * rather than in Epic 2's Kotlin code; reconciling the two is Epic 2's follow-up, not this task's.
 */
public record IngestItemResult(String transactionId, int status) {}
