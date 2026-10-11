package com.codepilot.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One @Bean method decides, from configuration, which EmbeddingClient gets injected everywhere
 * else. Adding a provider means adding one branch here - nothing else in the application changes.
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    /**
     * Every provider here produces 1024-dimension vectors, matching the vector(1024) column from
     * V3. A provider with a different width needs a migration AND a full re-index.
     *
     * ⚠️ SWITCHING PROVIDERS ALWAYS REQUIRES A FULL RE-INDEX, even at the same width. Vectors
     * from different models live in different spaces - Postgres stores them happily, nothing
     * errors, and similarity search silently returns noise. There is deliberately no automatic
     * fallback between providers for exactly this reason: a fallback would mix two spaces in
     * one table and quietly destroy retrieval quality with no failure anywhere.
     */
    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties properties) {
        return switch (properties.getProvider()) {
            case "jina"     -> new JinaEmbeddingClient(properties, 1024);
            case "voyage"   -> new VoyageEmbeddingClient(properties, 1024);
            case "ollama"   -> new OllamaEmbeddingClient(properties, 1024);
            case "gemini"   -> new GeminiEmbeddingClient(properties, 1024);
            case "nemotron" -> new NemotronEmbeddingClient(properties, 1024);
            default -> throw new IllegalStateException(
                    "Unknown embedding provider: " + properties.getProvider()
                            + " (expected one of: jina, voyage, ollama, gemini, nemotron)");
        };
    }
}