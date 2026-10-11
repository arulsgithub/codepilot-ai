package com.codepilot.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embeddings from Voyage AI - a hosted provider, which is what makes deployment possible.
 * Ollama runs on localhost, and a hosted backend has no localhost.
 *
 * Model: voyage-code-4, trained specifically for code retrieval. Default output is exactly
 * 1024 dimensions, so it drops into the existing vector(1024) column with no migration - but
 * a FULL RE-INDEX is still mandatory. Vectors from different models occupy different spaces;
 * Postgres will store them happily and comparison returns noise, with no error anywhere.
 */
public class VoyageEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingClient.class);

    private static final String DEFAULT_BASE_URL = "https://api.voyageai.com/v1";
    private static final String DEFAULT_MODEL = "voyage-code-4";

    private final WebClient webClient;
    private final String model;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VoyageEmbeddingClient(EmbeddingProperties properties, int dimensions) {

        String apiKey = properties.getApiKey();
        // Same guard as elsewhere: an unresolved ${VOYAGE_API_KEY} placeholder would otherwise
        // be sent as a literal Bearer token and come back as a baffling 401.
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException(
                    "Voyage embeddings need an API key. Set the VOYAGE_API_KEY environment "
                            + "variable, then restart the terminal AND the IDE so it is picked up.");
        }

        String baseUrl = resolve(properties.getBaseUrl(), DEFAULT_BASE_URL);
        this.model = resolve(properties.getModel(), DEFAULT_MODEL);
        this.dimensions = dimensions;

        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofMinutes(2));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                // A batch response is several hundred KB of JSON floats, past the 256 KB default.
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();

        log.info("Voyage embeddings: model={}, dimensions={}", model, dimensions);
    }

    @Override
    public List<Float> embed(String text, InputType inputType) {
        return embedBatch(List.of(text), inputType).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, InputType inputType) {

        // Voyage handles query/passage asymmetry natively rather than by prefixing the text
        // ourselves. Same concept as mxbai's prefix and Gemini's taskType: a question and the
        // code that answers it are different kinds of object, and telling the model which is
        // which measurably improves retrieval.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", texts);
        body.put("model", model);
        body.put("input_type", inputType == InputType.QUERY ? "query" : "document");
        body.put("output_dimension", dimensions);

        String response;
        try {
            response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    // TWO failure families, and both must be handled - the lesson from the
                    // connection reset that killed a full indexing run:
                    //   WebClientRequestException = no response at all (transport)
                    //   WebClientResponseException 429/5xx = a response, but retryable
                    // A 400 or 401 is NOT retryable and must surface immediately.
                    .retryWhen(Retry.backoff(6, Duration.ofSeconds(5))
                            .maxBackoff(Duration.ofSeconds(90))
                            .filter(VoyageEmbeddingClient::isRetryable)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();

        } catch (WebClientResponseException e) {
            throw new IllegalStateException(friendly(e), e);
        } catch (WebClientRequestException e) {
            throw new IllegalStateException(
                    "Could not reach the Voyage API: " + e.getMessage(), e);
        }

        return parse(response);
    }

    private static boolean isRetryable(Throwable t) {
        if (t instanceof WebClientRequestException || t instanceof java.io.IOException) {
            return true;
        }
        if (t instanceof WebClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    private String friendly(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 401 -> "Voyage rejected the API key (401). Check VOYAGE_API_KEY is correct "
                    + "and that the terminal/IDE was restarted after setting it.";
            case 402 -> "Voyage reports the free token allowance is exhausted (402). Add a "
                    + "payment method, or switch ai.embedding.provider back to ollama "
                    + "(which requires a full re-index).";
            case 429 -> "Voyage rate limit (429). Body: " + e.getResponseBodyAsString()
                    + " | Free accounts have a much lower requests-per-minute limit than the "
                    + "published Tier 1 figures. Because the limit counts REQUESTS, the fix is "
                    + "fewer and larger batches plus longer pacing - not smaller batches.";
            default -> "Voyage returned " + status + ": " + e.getResponseBodyAsString();
        };
    }

    private List<List<Float>> parse(String response) {
        JsonNode json;
        try {
            json = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Voyage response: " + response, e);
        }

        // Voyage returns the OpenAI-compatible shape: {"data":[{"embedding":[...],"index":0}]}.
        // Some responses/versions use a flat "embeddings" array instead, so both are accepted -
        // cheaper than debugging an NPE against a live API.
        List<JsonNode> vectorNodes = new ArrayList<>();
        JsonNode data = json.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                JsonNode embedding = item.get("embedding");
                if (embedding != null && embedding.isArray()) {
                    vectorNodes.add(embedding);
                }
            }
        } else {
            JsonNode embeddings = json.get("embeddings");
            if (embeddings != null && embeddings.isArray()) {
                embeddings.forEach(vectorNodes::add);
            }
        }

        if (vectorNodes.isEmpty()) {
            throw new IllegalStateException("Voyage returned no embeddings. Response: " + response);
        }

        List<List<Float>> results = new ArrayList<>(vectorNodes.size());
        for (JsonNode node : vectorNodes) {
            List<Float> vector = new ArrayList<>(node.size());
            for (JsonNode value : node) {
                vector.add(value.floatValue());
            }
            if (vector.size() != dimensions) {
                // Caught here rather than as an opaque Postgres error thousands of rows later.
                throw new IllegalStateException("Model '" + model + "' returned " + vector.size()
                        + "-dimension vectors, but the database column expects " + dimensions + ".");
            }
            results.add(vector);
        }
        return results;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private static String resolve(String configured, String fallback) {
        return (configured == null || configured.isBlank() || configured.startsWith("${"))
                ? fallback
                : configured;
    }
}