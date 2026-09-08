package com.codepilot.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

public class NemotronEmbeddingClient implements EmbeddingClient {

    private final WebClient webClient;
    private final String model;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NemotronEmbeddingClient(EmbeddingProperties properties, int dimensions) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                // A batch embedding response (20 chunks x 1024+ floats as JSON text) is
                // several hundred KB, well past WebClient's 256 KB default in-memory buffer.
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.model = properties.getModel();
        this.dimensions = dimensions;
    }

    @Override
    public List<Float> embed(String text, InputType inputType) {
        return embedBatch(List.of(text), inputType).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, InputType inputType) {
        var requestBody = new java.util.HashMap<String, Object>();
        requestBody.put("model", model);
        requestBody.put("input", texts);
        // "passage" for code we're indexing/storing, "query" for a live user question -
        // NVIDIA's docs call this "very important" for retrieval accuracy.
        requestBody.put("input_type", inputType == InputType.QUERY ? "query" : "passage");
        requestBody.put("encoding_format", "float");
        requestBody.put("truncate", "NONE");

        String body = webClient.post()
                .uri("/embeddings") // baseUrl already ends in /v1
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode response;
        try {
            response = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Nemotron embeddings response: " + body, e);
        }

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