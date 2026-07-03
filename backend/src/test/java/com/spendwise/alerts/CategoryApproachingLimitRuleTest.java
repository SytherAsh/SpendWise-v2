package com.spendwise.alerts;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Required test for E5-S2-T2 (docs/testing.md Budget unit tests: 80% per-category threshold, medium priority). */
class CategoryApproachingLimitRuleTest {

    @Test
    void eightyPercentTriggers() {
        assertThat(CategoryApproachingLimitRule.triggers(BigDecimal.valueOf(80), BigDecimal.valueOf(100))).isTrue();
    }

    @Test
    void belowEightyPercentDoesNotTrigger() {
        assertThat(CategoryApproachingLimitRule.triggers(BigDecimal.valueOf(79), BigDecimal.valueOf(100))).isFalse();
    }

    @Test
    void atOrAboveOneHundredPercentDoesNotTrigger() {
        // E5-S2-T2 DoD boundary rule — overspend (CategoryOverspendRule) takes over at >=100%.
        assertThat(CategoryApproachingLimitRule.triggers(BigDecimal.valueOf(100), BigDecimal.valueOf(100))).isFalse();
        assertThat(CategoryApproachingLimitRule.triggers(BigDecimal.valueOf(150), BigDecimal.valueOf(100))).isFalse();
    }
}
