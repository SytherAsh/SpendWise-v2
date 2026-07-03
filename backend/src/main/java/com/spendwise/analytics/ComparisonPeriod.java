package com.spendwise.analytics;

import java.time.Instant;
import java.util.List;

/** One side (current or previous) of `GET /analytics/comparison` (E7-S1-T3). */
public record ComparisonPeriod(Instant from, Instant to, OverallTotals overall, List<CategoryTotal> categories) {}
