package com.spendwise.analytics;

import java.math.BigDecimal;
import java.time.Instant;

/** One time bucket of `GET /analytics/trends` (E7-S1-T4) — spend only, per the endpoint's "spending trend" scope. */
public record TrendBucket(Instant bucketStart, BigDecimal totalSpend) {}
