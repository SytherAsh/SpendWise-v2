package com.spendwise.common.llm.provider;

import com.spendwise.common.llm.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StubLlmClientTest {

    private final StubLlmClient client = new StubLlmClient();

    @Test
    void rendersTheRecommendationTemplateForItsWellKnownContextShape() {
        Map<String, Object> context = Map.of(
                "categoryName", "Food",
                "currentMonthSpend", "3200",
                "previousMonthSpend", "2318",
                "percentIncrease", "38");

        LlmResponse response = client.complete("Generate a savings recommendation.", context);

        assertThat(response.text()).isEqualTo("You spent ₹3200 on Food this month — 38% more than last month.");
    }

    @Test
    void isDeterministicForTheSameInput() {
        Map<String, Object> context = Map.of("foo", "bar");

        LlmResponse first = client.complete("prompt", context);
        LlmResponse second = client.complete("prompt", context);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void fallsBackToAGenericEchoForAnUnrecognizedContextShape() {
        LlmResponse response = client.complete("How much did I spend?", Map.of("recentTransactionCount", 14));

        assertThat(response.text()).isEqualTo("How much did I spend? [recentTransactionCount=14]");
    }

    @Test
    void fallsBackToThePromptAloneForEmptyContext() {
        LlmResponse response = client.complete("hello", Map.of());

        assertThat(response.text()).isEqualTo("hello");
    }
}
