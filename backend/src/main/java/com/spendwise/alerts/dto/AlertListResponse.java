package com.spendwise.alerts.dto;

import java.util.List;
import java.util.UUID;

/** Matches docs/api.md Pagination response shape exactly: {data, nextCursor, hasMore}. */
public record AlertListResponse(List<AlertResponse> data, UUID nextCursor, boolean hasMore) {}
