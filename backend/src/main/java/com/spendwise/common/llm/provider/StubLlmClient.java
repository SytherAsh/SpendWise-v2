package com.spendwise.common.llm.provider;

import com.spendwise.common.llm.LlmClient;
import com.spendwise.common.llm.LlmResponse;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministic {@link LlmClient} used in local dev and every automated test (E8-S1-T1) — no
 * network call, no vendor SDK, no API key. Never referenced outside this package (see {@code
 * LlmBoundaryTest}); callers reach it only through {@link com.spendwise.common.llm.LlmConfig}'s
 * {@code LlmClient} bean.
 *
 * <p>Recognizes one well-known context shape (the four keys populated by {@code
 * RecommendationGeneratorJob}) and renders the exact one-liner style from
 * docs/requirements.md's own example ("You spent ₹3,200 on Food this month — 38% more
 * than last month"). For any other context shape (e.g. Chatbot's), falls back to a generic,
 * deterministic echo of the context map so callers can still assert on grounding data reaching
 * this class without the stub needing to understand every caller's domain. A real provider
 * wouldn't need this special-casing at all — it would generate prose from context itself.
 */
public class StubLlmClient implements LlmClient {

    private static final String CATEGORY_NAME_KEY = "categoryName";
    private static final String CURRENT_MONTH_SPEND_KEY = "currentMonthSpend";
    private static final String PREVIOUS_MONTH_SPEND_KEY = "previousMonthSpend";
    private static final String PERCENT_INCREASE_KEY = "percentIncrease";

    @Override
    public LlmResponse complete(String prompt, Map<String, Object> context) {
        if (context.containsKey(CATEGORY_NAME_KEY)
                && context.containsKey(CURRENT_MONTH_SPEND_KEY)
                && context.containsKey(PREVIOUS_MONTH_SPEND_KEY)
                && context.containsKey(PERCENT_INCREASE_KEY)) {
            return new LlmResponse(
                    "You spent ₹%s on %s this month — %s%% more than last month."
                            .formatted(
                                    context.get(CURRENT_MONTH_SPEND_KEY),
                                    context.get(CATEGORY_NAME_KEY),
                                    context.get(PERCENT_INCREASE_KEY)));
        }
        String contextSummary = context.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return new LlmResponse(contextSummary.isBlank() ? prompt : prompt + " [" + contextSummary + "]");
    }
}
