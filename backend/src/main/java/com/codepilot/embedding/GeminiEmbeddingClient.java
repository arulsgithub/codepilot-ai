package com.codepilot.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeminiEmbeddingClient implements EmbeddingClient {

    private final WebClient webClient;
    private final String apiKey;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiEmbeddingClient(EmbeddingProperties properties, int dimensions) {
        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                // A batch embedding response (20 chunks x 1024+ floats as JSON text) is
                // several hundred KB, well past WebClient's 256 KB default in-memory buffer.
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set or not resolved - check your environment variables. " +
                            "Got: " + apiKey
            );
        }
        this.dimensions = dimensions;
    }

    @Override
    public List<Float> embed(String text, InputType inputType) {
        return embedBatch(List.of(text), inputType).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, InputType inputType) {
        // CODE_RETRIEVAL_QUERY when embedding a user's question about code, RETRIEVAL_DOCUMENT
        // when embedding the code itself for storage - Gemini uses this to bias the vector
        // toward "this text is being searched FOR" vs "this text is being searched THROUGH".
        String taskType = inputType == InputType.QUERY ? "CODE_RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT";

        List<Map<String, Object>> requests = texts.stream()
                .map(text -> Map.<String, Object>of(
                        "model", "models/gemini-embedding-001",
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "taskType", taskType,
                        "outputDimensionality", dimensions
                ))
                .toList();

        String response;
        try {
            response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/gemini-embedding-001:batchEmbedContents")
                            .queryParam("key", apiKey)
                            .build())
                    .bodyValue(Map.of("requests", requests))
                    .retrieve()
                    .bodyToMono(String.class)   // fetch as raw text, not JsonNode - sidesteps the WebFlux codec issue
                    // Gemini's embedding endpoint has tight rate limits; a burst of batches
                    // during indexing gets 429s. Back off and retry rather than failing the run.
                    .retryWhen(Retry.backoff(5, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(60))
                            .filter(GeminiEmbeddingClient::isRetryable)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "Gemini embeddings request failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Gemini embeddings response: " + response, e);
        }

        List<List<Float>> results = new ArrayList<>();
        for (JsonNode item : json.get("embeddings")) {
            List<Float> vector = new ArrayList<>();
            for (JsonNode value : item.get("values")) {
                vector.add(value.floatValue());
            }
            results.add(vector);
        }
        return results;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private static boolean isRetryable(Throwable t) {
        return t instanceof WebClientResponseException e
                && (e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError());
    }
}