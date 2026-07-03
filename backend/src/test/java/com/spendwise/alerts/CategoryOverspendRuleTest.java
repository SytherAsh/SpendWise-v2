package com.spendwise.alerts;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Required test for E5-S2-T3 (docs/testing.md Budget unit tests: category budget overspend threshold, high priority). */
class CategoryOverspendRuleTest {

    @Test
    void exactlyOneHundredPercentTriggers() {
        assertThat(CategoryOverspendRule.triggers(BigDecimal.valueOf(100), BigDecimal.valueOf(100))).isTrue();
    }

    @Test
    void aboveOneHundredPercentTriggers() {
        assertThat(CategoryOverspendRule.triggers(BigDecimal.valueOf(150), BigDecimal.valueOf(100))).isTrue();
    }

    @Test
    void belowOneHundredPercentDoesNotTrigger() {
        assertThat(CategoryOverspendRule.triggers(BigDecimal.valueOf(99), BigDecimal.valueOf(100))).isFalse();
    }
}
