package com.spendwise.alerts;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * E5-S2-T2 — is a single category's spend at 80%+ but below 100% of its budget? docs/requirements.md
 * Alerts priority table ("Medium — 80% — in-app only"). At or above 100% is
 * {@link CategoryOverspendRule}'s territory instead — the boundary rule the epic asks for is
 * enforced by the caller checking overspend first and only evaluating this rule if it didn't fire.
 */
public final class CategoryApproachingLimitRule {

    private static final BigDecimal LOWER_BOUND = new BigDecimal("0.8");
    private static final BigDecimal UPPER_BOUND = BigDecimal.ONE;

    private CategoryApproachingLimitRule() {}

    /** Fixture at 80%+ but below 100% triggers; below 80% does not (E5-S2-T2 DoD). */
    public static boolean triggers(BigDecimal spent, BigDecimal categoryLimit) {
        if (categoryLimit.signum() <= 0) {
            return false;
        }
        BigDecimal ratio = spent.divide(categoryLimit, 6, RoundingMode.HALF_UP);
        return ratio.compareTo(LOWER_BOUND) >= 0 && ratio.compareTo(UPPER_BOUND) < 0;
    }
}
