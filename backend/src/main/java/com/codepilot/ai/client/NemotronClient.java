package com.codepilot.ai.client;

import com.codepilot.ai.dto.LLMRequest;
import com.codepilot.ai.dto.LLMResponse;
import com.codepilot.ai.dto.LLMStreamChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Objects;

@Component
public class NemotronClient implements LLMClient {

    private final WebClient webClient;
    private final NemotronProperties properties;
    private final ObjectMapper objectMapper;

    public NemotronClient(
            WebClient nemotronWebClient,
            NemotronProperties properties,
            ObjectMapper objectMapper) {

        this.webClient = nemotronWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LLMResponse chat(LLMRequest request) {

        return webClient
                .post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(LLMResponse.class)
                .block();
    }

    @Override
    public Flux<String> streamChat(LLMRequest request) {

        LLMRequest streamingRequest = new LLMRequest(
                request.model(),
                request.messages(),
                request.temperature(),
                true
        );

        return webClient
                .post()
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
            LLMStreamChunk chunk =
                    objectMapper.readValue(json, LLMStreamChunk.class);

            if (chunk.choices() == null || chunk.choices().isEmpty()) {
                return Flux.empty();
            }

            LLMStreamChunk.Delta delta =
                    chunk.choices().getFirst().delta();

            if (delta == null) {
                return Flux.empty();
            }

            String content = delta.content();

            if (content == null || content.isEmpty()) {
                return Flux.empty();
            }

            return Flux.just(content);

        } catch (Exception e) {

            return Flux.error(
                    new IllegalStateException(
                            "Failed to parse Nemotron streaming response",
                            e
                    )
            );
        }
    }
}