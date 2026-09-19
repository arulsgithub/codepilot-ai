package com.codepilot.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same pattern as AIClientConfig for chat: one @Bean method decides, from configuration, which
 * EmbeddingClient implementation gets injected everywhere else. Adding a provider means adding
 * one branch here - nothing else in the application changes.
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    /**
     * All three providers currently produce 1024-dimension vectors, matching the vector(1024)
     * column created in V3. Any provider added with a different width needs a migration AND a
     * full re-index - vectors from different models are not comparable, so they cannot be mixed
     * in one table.
     */
    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties properties) {
        return switch (properties.getProvider()) {
            case "ollama" -> new OllamaEmbeddingClient(properties, 1024);
            case "gemini" -> new GeminiEmbeddingClient(properties, 1024);
            case "nemotron" -> new NemotronEmbeddingClient(properties, 1024);
            default -> throw new IllegalStateException(
                    "Unknown embedding provider: " + properties.getProvider()
                            + " (expected one of: ollama, gemini, nemotron)");
        };
    }
}