package com.codepilot.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ai.embedding.provider selects which EmbeddingClient bean is built (see EmbeddingConfig).
 *
 * batch-size and pacing-millis were hardcoded constants in RepositoryIndexingService, which
 * meant switching provider required editing Java and rebuilding. They belong here because the
 * right values are a property OF THE PROVIDER: Ollama wants big batches and no pacing, a
 * throttled hosted tier wants small batches and long pauses.
 */
@ConfigurationProperties(prefix = "ai.embedding")
public class EmbeddingProperties {

    private String provider = "ollama";
    private String apiKey;
    private String baseUrl;
    private String model;

    /**
     * How many chunks go in one request.
     *
     * The right value depends on WHICH limit binds. Under a requests-per-minute cap, bigger is
     * better (fewer requests). Under a tokens-per-minute cap, bigger is worse - one oversized
     * request can exceed the whole minute's token budget on its own, which is exactly what
     * happened with Voyage's free 10K TPM.
     */
    private int batchSize = 20;

    /** Milliseconds to wait between batches. 0 for a local server with no rate limit. */
    private long pacingMillis = 0;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public long getPacingMillis() { return pacingMillis; }
    public void setPacingMillis(long pacingMillis) { this.pacingMillis = pacingMillis; }
}