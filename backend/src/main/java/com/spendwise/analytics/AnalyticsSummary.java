package com.spendwise.analytics;

import java.util.List;

/** docs/api.md `GET /analytics/summary` (E7-S1-T1). */
public record AnalyticsSummary(OverallTotals overall, List<CategoryTotal> categories) {}
