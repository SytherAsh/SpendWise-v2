package com.spendwise.alerts;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * E5-S2-T3 — is a single category's spend at or beyond 100% of its budget? docs/requirements.md
 * Alerts priority table ("High — budget exceeded — push + email"). Exactly-once suppression per
 * month is enforced by the caller (see {@link AlertsService#recordIfNotAlreadyTriggeredThisMonth}),
 * not by this pure evaluation function.
 */
public final class CategoryOverspendRule {

    private CategoryOverspendRule() {}

    /** Fixture at >=100% triggers (E5-S2-T3 DoD). */
    public static boolean triggers(BigDecimal spent, BigDecimal categoryLimit) {
        if (categoryLimit.signum() <= 0) {
            return false;
        }
        BigDecimal ratio = spent.divide(categoryLimit, 6, RoundingMode.HALF_UP);
        return ratio.compareTo(BigDecimal.ONE) >= 0;
    }
}
