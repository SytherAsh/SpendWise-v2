package com.spendwise.analytics.dto;

import com.spendwise.analytics.AnalyticsTrends;

import java.util.List;

/** docs/api.md `GET /analytics/trends` (E7-S1-T4). */
public record AnalyticsTrendsResponse(String granularity, List<TrendBucketResponse> buckets) {

    public static AnalyticsTrendsResponse from(AnalyticsTrends trends) {
        return new AnalyticsTrendsResponse(
                trends.granularity(), trends.buckets().stream().map(TrendBucketResponse::from).toList());
    }
}
