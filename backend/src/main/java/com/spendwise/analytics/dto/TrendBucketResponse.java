package com.spendwise.analytics.dto;

import com.spendwise.analytics.TrendBucket;

import java.math.BigDecimal;
import java.time.Instant;

public record TrendBucketResponse(Instant bucketStart, BigDecimal totalSpend) {

    public static TrendBucketResponse from(TrendBucket bucket) {
        return new TrendBucketResponse(bucket.bucketStart(), bucket.totalSpend());
    }
}
