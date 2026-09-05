package com.codepilot.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nemotron")
public record NemotronProperties(
        String baseUrl,
        String apiKey,
        String model
) {
}