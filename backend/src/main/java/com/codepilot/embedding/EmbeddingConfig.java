package com.codepilot.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same pattern as your existing AIClientConfig for chat: one @Bean method decides, based on
 * configuration, which EmbeddingClient implementation becomes the Spring bean that gets
 * injected everywhere else. Adding a second provider later means adding one more branch here -
 * nothing else in the app changes.
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    @Bean
    public EmbeddingClient embeddingClient(EmbeddingProperties properties) {
        return switch (properties.getProvider()) {
            case "nemotron" -> new NemotronEmbeddingClient(properties, 1024); // adjust dimensions to your model's actual output size
            default -> throw new IllegalStateException("Unknown embedding provider: " + properties.getProvider());
        };
    }
}