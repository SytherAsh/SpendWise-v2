package com.spendwise.alerts;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Required test for E5-S2-T1 (docs/testing.md Budget unit tests: mid-month 50% total-budget threshold). */
class MidMonthBudgetRuleTest {

    @Test
    void exactlyFiftyPercentByTheFifteenthTriggers() {
        assertThat(MidMonthBudgetRule.triggers(BigDecimal.valueOf(500), BigDecimal.valueOf(1000), 15)).isTrue();
    }

    @Test
    void belowFiftyPercentDoesNotTrigger() {
        assertThat(MidMonthBudgetRule.triggers(BigDecimal.valueOf(499), BigDecimal.valueOf(1000), 15)).isFalse();
    }

    @Test
    void fiftyPercentBeforeTheFifteenthDoesNotTrigger() {
        assertThat(MidMonthBudgetRule.triggers(BigDecimal.valueOf(500), BigDecimal.valueOf(1000), 14)).isFalse();
    }

    @Test
    void zeroTotalBudgetNeverTriggers() {
        assertThat(MidMonthBudgetRule.triggers(BigDecimal.valueOf(100), BigDecimal.ZERO, 20)).isFalse();
    }
}
