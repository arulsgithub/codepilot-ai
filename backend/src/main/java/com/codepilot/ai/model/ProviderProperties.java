package com.codepilot.ai.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds ai.providers.<name>.base-url / .api-key
 * e.g. ai.providers.groq.base-url=https://api.groq.com/openai/v1
 */
@ConfigurationProperties(prefix = "ai")
public class ProviderProperties {

    private Map<String, Provider> providers = new HashMap<>();

    public Map<String, Provider> getProviders() { return providers; }
    public void setProviders(Map<String, Provider> providers) { this.providers = providers; }

    public static class Provider {
        private String baseUrl;
        private String apiKey;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}