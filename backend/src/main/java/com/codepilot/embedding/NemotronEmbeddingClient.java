package com.codepilot.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Calls Nemotron's embedding endpoint (NVIDIA NIM-compatible embeddings API - same request
 * shape as OpenAI's /embeddings). Written against WebClient like NemotronClient already does
 * for chat, so we're not introducing a second HTTP client library.
 *
 * NOTE: confirm the exact embedding model name/endpoint path with your NVIDIA NIM account
 * before first real use - this targets the standard "/v1/embeddings" NIM path with an
 * OpenAI-compatible payload, which is correct for most NIM-hosted embedding models (e.g.
 * NV-Embed-QA) but tell me if your account's path differs and we'll adjust just this class.
 */
public class NemotronEmbeddingClient implements EmbeddingClient {

    private final WebClient webClient;
    private final String model;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NemotronEmbeddingClient(EmbeddingProperties properties, int dimensions) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
        this.model = properties.getModel();
        this.dimensions = dimensions;
    }

    @Override
    public List<Float> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        var requestBody = new java.util.HashMap<String, Object>();
        requestBody.put("model", model);
        requestBody.put("input", texts);

        JsonNode response = webClient.post()
                .uri("/v1/embeddings")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<List<Float>> results = new ArrayList<>();
        for (JsonNode item : response.get("data")) {
            List<Float> vector = new ArrayList<>();
            for (JsonNode value : item.get("embedding")) {
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
}