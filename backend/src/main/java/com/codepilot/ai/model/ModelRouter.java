package com.codepilot.ai.model;

import com.codepilot.ai.client.LLMClient;
import com.codepilot.ai.client.NemotronClient;
import com.codepilot.ai.client.NemotronProperties;
import com.codepilot.ai.client.OpenAiCompatibleClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final Map<String, LLMClient> clientsByProvider = new HashMap<>();
    private final ModelRoutingProperties routingProperties;
    private final NemotronProperties nemotronProperties;

    public ModelRouter(NemotronClient nemotronClient,
                       NemotronProperties nemotronProperties,
                       ProviderProperties providerProperties,
                       ModelRoutingProperties routingProperties,
                       WebClient.Builder webClientBuilder,
                       ObjectMapper objectMapper) {

        this.routingProperties = routingProperties;
        this.nemotronProperties = nemotronProperties;

        // The existing, working Nemotron client stays exactly as-is and is simply registered
        // under the name "nemotron" so config can still route to it.
        clientsByProvider.put("nemotron", nemotronClient);

        // Every other configured provider gets a generic OpenAI-compatible client.
        providerProperties.getProviders().forEach((name, provider) -> {
            if ("nemotron".equals(name)) {
                return; // already registered above
            }
            boolean unconfigured = provider.getBaseUrl() == null
                    || provider.getApiKey() == null
                    || provider.getApiKey().isBlank()
                    || provider.getApiKey().startsWith("${");
            if (unconfigured) {
                // Skip rather than fail: you should be able to add providers one at a time
                // without the app refusing to start. Routing TO this provider still fails
                // loudly (see resolve()), so a real misconfiguration is never silent.
                log.warn("Skipping LLM provider '{}' - no API key configured. "
                        + "Any mode routed to it will fail until you set it.", name);
                return;
            }
            clientsByProvider.put(name, new OpenAiCompatibleClient(
                    name, provider.getBaseUrl(), provider.getApiKey(), webClientBuilder, objectMapper));
            log.info("Registered LLM provider: {} ({})", name, provider.getBaseUrl());
        });
    }

    /**
     * @param mode what kind of work this request is
     * @return the client + model name to use
     */
    public ResolvedModel resolve(ModelMode mode) {
        String configured = routingProperties.getModels().get(mode);

        // No mapping configured for this mode -> fall back to Nemotron so the app still works
        // rather than 500-ing. Logged at WARN so a missing config line is visible.
        if (configured == null || configured.isBlank()) {
            log.warn("No model configured for mode {} (ai.models.{}), falling back to Nemotron",
                    mode, mode.name().toLowerCase());
            return new ResolvedModel(clientsByProvider.get("nemotron"), nemotronProperties.model());
        }

        // Split on the FIRST colon only: provider names never contain ':', but model IDs can
        // (e.g. "z-ai/glm-5.2:nitro"), so everything after the first colon is the model.
        int separator = configured.indexOf(':');
        if (separator < 0) {
            throw new IllegalStateException(
                    "Invalid ai.models." + mode.name().toLowerCase() + " value '" + configured
                            + "' - expected format <provider>:<model>");
        }
        String providerName = configured.substring(0, separator);
        String modelName = configured.substring(separator + 1);

        LLMClient client = clientsByProvider.get(providerName);
        if (client == null) {
            throw new IllegalStateException(
                    "Unknown provider '" + providerName + "' for mode " + mode
                            + ". Configured providers: " + clientsByProvider.keySet());
        }
        return new ResolvedModel(client, modelName);
    }
}