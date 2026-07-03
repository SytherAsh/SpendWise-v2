package com.spendwise.common.llm;

import com.spendwise.common.llm.provider.StubLlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single, config-driven provider-selection point (E8-S1-T1 DoD: "swapping the stub for a real
 * provider requires touching only this layer's implementation"). Today {@code app.llm.provider}
 * (env {@code LLM_PROVIDER}, default {@code stub}) has exactly one valid value — adding a real
 * vendor later means adding a new class to {@code com.spendwise.common.llm.provider} and a new
 * {@code case} below, never touching Recommendations or Chatbot.
 */
@Configuration
public class LlmConfig {

    @Bean
    public LlmClient llmClient(@Value("${app.llm.provider}") String provider) {
        return switch (provider) {
            case "stub" -> new StubLlmClient();
            default -> throw new IllegalArgumentException("Unknown app.llm.provider: " + provider);
        };
    }
}
