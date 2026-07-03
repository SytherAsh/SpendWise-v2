package com.spendwise.alerts;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * E5-S2-T1 — has the user spent 50% of their *total* monthly budget by mid-month?
 * docs/requirements.md Alerts table ("Mid-month budget alert"); priority {@code high}.
 */
public final class MidMonthBudgetRule {

    private static final int MID_MONTH_DAY = 15;
    private static final BigDecimal THRESHOLD = new BigDecimal("0.5");

    private MidMonthBudgetRule() {}

    /** Fixture at exactly 50% by the 15th triggers; below 50%, or before the 15th, does not (E5-S2-T1 DoD). */
    public static boolean triggers(BigDecimal totalSpent, BigDecimal totalBudget, int dayOfMonth) {
        if (dayOfMonth < MID_MONTH_DAY || totalBudget.signum() <= 0) {
            return false;
        }
        BigDecimal ratio = totalSpent.divide(totalBudget, 6, RoundingMode.HALF_UP);
        return ratio.compareTo(THRESHOLD) >= 0;
    }
}
