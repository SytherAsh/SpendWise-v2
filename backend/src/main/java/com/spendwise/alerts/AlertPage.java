package com.spendwise.alerts;

import java.util.List;
import java.util.UUID;

/** Service-layer cursor page result; the wire-shape counterpart is {@code dto.AlertListResponse}. */
public record AlertPage(List<Alert> data, UUID nextCursor, boolean hasMore) {}
