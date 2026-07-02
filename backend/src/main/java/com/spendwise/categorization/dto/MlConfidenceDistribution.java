package com.spendwise.categorization.dto;

/** Mirrors ml/api/schemas.py's {@code ConfidenceDistribution}. */
public record MlConfidenceDistribution(double mean, double median, double min, double max) {}
