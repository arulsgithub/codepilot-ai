package com.codepilot.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai.embedding.provider selects which EmbeddingClient bean is primary (see EmbeddingConfig).
 * Kept separate from NemotronProperties since embeddings may use a totally different provider
 * (or the same Nemotron key/base-url reused for its embedding endpoint - configurable either way).
 */
@ConfigurationProperties(prefix = "ai.embedding")
public class EmbeddingProperties {

    private String provider = "nemotron"; // "nemotron" or "local" for now
    private String apiKey;
    private String baseUrl;
    private String model;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}