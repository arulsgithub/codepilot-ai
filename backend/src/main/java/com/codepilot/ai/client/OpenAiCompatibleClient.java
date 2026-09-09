package com.codepilot.ai.client;

import com.codepilot.ai.dto.LLMRequest;
import com.codepilot.ai.dto.LLMResponse;
import com.codepilot.ai.dto.LLMStreamChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * One client for any OpenAI-compatible provider (Groq, OpenRouter, ...). Not a @Component -
 * ModelRouter instantiates one per configured provider, since each needs its own base URL/key.
 *
 * This intentionally duplicates NemotronClient's logic rather than refactoring it, so the
 * working Nemotron path carries zero risk from this change. Once multi-model is proven in
 * production, NemotronClient could be deleted and routed through this class instead - worth
 * doing eventually, but not while we're changing several things at once.
 */
public class OpenAiCompatibleClient implements LLMClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String providerName;

    public OpenAiCompatibleClient(String providerName, String baseUrl, String apiKey,
                                  WebClient.Builder builder, ObjectMapper objectMapper) {
        this.providerName = providerName;
        this.objectMapper = objectMapper;
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(LLMResponse.class)
                .block();
    }

    @Override
    public Flux<String> streamChat(LLMRequest request) {
        LLMRequest streamingRequest = new LLMRequest(
                request.model(), request.messages(), request.temperature(), true);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(streamingRequest)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .map(ServerSentEvent::data)
                .filter(Objects::nonNull)
                .filter(data -> !data.equals("[DONE]"))
                .flatMap(this::extractContent);
    }

    private Flux<String> extractContent(String json) {
        try {
            LLMStreamChunk chunk = objectMapper.readValue(json, LLMStreamChunk.class);
            if (chunk.choices() == null || chunk.choices().isEmpty()) {
                return Flux.empty();
            }
            LLMStreamChunk.Delta delta = chunk.choices().getFirst().delta();
            if (delta == null) {
                return Flux.empty();
            }
            String content = delta.content();
            if (content == null || content.isEmpty()) {
                return Flux.empty();
            }
            return Flux.just(content);
        } catch (Exception e) {
            return Flux.error(new IllegalStateException(
                    "Failed to parse " + providerName + " streaming response", e));
        }
    }
}