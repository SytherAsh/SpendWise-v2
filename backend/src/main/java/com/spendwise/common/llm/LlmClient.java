package com.spendwise.common.llm;

import java.util.Map;

/**
 * Vendor-neutral LLM interface (E8-S1-T1; CLAUDE.md "LLM: Provider intentionally abstracted...
 * Do not hardcode any LLM SDK into business logic"). Recommendations and Chatbot depend only on
 * this interface, never on a concrete provider class — enforced by {@code LlmBoundaryTest}, which
 * blocks any class outside {@code com.spendwise.common.llm.provider} from depending on classes in
 * that package. Swapping the stub for a real vendor SDK later requires adding a new class to that
 * package plus a new case in {@link LlmConfig}, not touching Recommendations/Chatbot at all.
 *
 * <p>{@code context} carries the grounding data (spend figures, category names, transaction
 * excerpts) the caller wants the response to reflect — reuses the same {@code Map<String,
 * Object>} payload idiom {@code com.spendwise.alerts.AlertsService} already uses for alert
 * payloads, rather than introducing a new typed hierarchy for a single-method interface.
 */
public interface LlmClient {

    LlmResponse complete(String prompt, Map<String, Object> context);
}
