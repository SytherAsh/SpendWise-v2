package com.spendwise.analytics;

import java.util.List;

/** docs/api.md `GET /analytics/trends` (E7-S1-T4). */
public record AnalyticsTrends(String granularity, List<TrendBucket> buckets) {}
