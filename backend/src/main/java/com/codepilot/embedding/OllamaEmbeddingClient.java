package com.codepilot.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embeddings from a locally running Ollama server - no API key, no quota, no network.
 *
 * Chosen model: mxbai-embed-large, which produces exactly 1024 dimensions and therefore drops
 * into the existing vector(1024) column with no migration. It is a general-purpose model rather
 * than a code-specialised one, so retrieval quality is a little below Gemini's
 * CODE_RETRIEVAL_QUERY - the trade for being unlimited and free.
 */
public class OllamaEmbeddingClient implements EmbeddingClient {

    /**
     * mxbai-embed-large is trained asymmetrically: QUERIES get this exact prefix, stored
     * PASSAGES get none. This is the model's documented usage, and it is the local equivalent
     * of Gemini's taskType distinction - getting it wrong measurably degrades retrieval.
     */
    private static final String QUERY_PREFIX =
            "Represent this sentence for searching relevant passages: ";

    private final WebClient webClient;
    private final String model;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaEmbeddingClient(EmbeddingProperties properties, int dimensions) {
        String baseUrl = (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getBaseUrl().startsWith("${"))
                ? "http://localhost:11434"
                : properties.getBaseUrl();

        // Local inference on CPU is far slower than a hosted API call - a batch of 20 chunks can
        // take tens of seconds. WebClient would otherwise give up long before Ollama replies.
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(5));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // A batch response is several hundred KB of JSON floats, well past the 256 KB default.
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();

        this.model = (properties.getModel() == null || properties.getModel().isBlank()
                || properties.getModel().startsWith("${"))
                ? "mxbai-embed-large"
                : properties.getModel();

        this.dimensions = dimensions;
    }

    @Override
    public List<Float> embed(String text, InputType inputType) {
        return embedBatch(List.of(text), inputType).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, InputType inputType) {

        List<String> prepared = texts.stream()
                .map(text -> inputType == InputType.QUERY ? QUERY_PREFIX + text : text)
                .toList();

        String response;
        try {
            response = webClient.post()
                    .uri("/api/embed")
                    .bodyValue(Map.of("model", model, "input", prepared))
                    .retrieve()
                    .bodyToMono(String.class)
                    // Ollama may still be loading the model into memory on the first call, which
                    // can refuse or drop the connection. Retry transport failures only - a real
                    // error (unknown model) comes back as a response and should surface at once.
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofSeconds(20))
                            .filter(t -> t instanceof WebClientRequestException
                                    || t instanceof java.io.IOException)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
        } catch (WebClientRequestException e) {
            throw new IllegalStateException(
                    "Could not reach Ollama. Is it running? Try: ollama serve  (and: ollama pull "
                            + model + "). Cause: " + e.getMessage(), e);
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Ollama response: " + response, e);
        }

        JsonNode embeddings = json.get("embeddings");
        if (embeddings == null || !embeddings.isArray()) {
            // Ollama reports unknown models and bad requests in an "error" field with HTTP 200-ish
            // shapes, so an absent "embeddings" array is the signal to look at the body.
            throw new IllegalStateException("Ollama returned no embeddings. Response: " + response);
        }

        List<List<Float>> results = new ArrayList<>();
        for (JsonNode item : embeddings) {
            List<Float> vector = new ArrayList<>(item.size());
            for (JsonNode value : item) {
                vector.add(value.floatValue());
            }
            if (vector.size() != dimensions) {
                // Caught here rather than as an opaque Postgres error thousands of rows later.
                throw new IllegalStateException("Model '" + model + "' returned " + vector.size()
                        + "-dimension vectors, but the database column expects " + dimensions
                        + ". Either change the model or add a migration for the new width.");
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