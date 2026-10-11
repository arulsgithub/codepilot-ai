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
 * Embeddings from Jina AI - the alternative hosted provider to Voyage.
 *
 * Model: jina-code-embeddings-1.5b, trained specifically for code retrieval. It uses Matryoshka
 * Representation Learning, so its native 1536 dimensions can be truncated to 1024 without
 * retraining and without meaningful quality loss - which is what lets it drop into the existing
 * vector(1024) column with no migration.
 *
 * The practical difference from Voyage is the free tier: Jina's key allows 100 RPM / 100K TPM
 * with no payment method, against Voyage's 3 RPM / 10K TPM. The trade is a much smaller free
 * token grant (~1M vs 200M), so Jina indexes faster but runs out sooner.
 */
public class JinaEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(JinaEmbeddingClient.class);

    private static final String DEFAULT_BASE_URL = "https://api.jina.ai/v1";
    private static final String DEFAULT_MODEL = "jina-code-embeddings-1.5b";

    /**
     * Jina's code model takes a task type rather than a generic query/passage flag, and
     * "nl2code" is precisely our situation: a natural-language question retrieving source code.
     * (Its other modes - code2code, code2nl, qa - exist for different retrieval shapes.)
     *
     * This is the same asymmetry idea as Voyage's input_type and mxbai's prefix string: a
     * question and the code answering it are different kinds of object, and telling the model
     * which is which measurably improves retrieval.
     */
    private static final String TASK_QUERY = "nl2code.query";
    private static final String TASK_PASSAGE = "nl2code.passage";

    private final WebClient webClient;
    private final String model;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JinaEmbeddingClient(EmbeddingProperties properties, int dimensions) {

        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalStateException(
                    "Jina embeddings need an API key. Get a free one at https://jina.ai/embeddings "
                            + "(no card required), set JINA_API_KEY, then restart the terminal AND the IDE.");
        }

        String baseUrl = resolve(properties.getBaseUrl(), DEFAULT_BASE_URL);
        this.model = resolve(properties.getModel(), DEFAULT_MODEL);
        this.dimensions = dimensions;

        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofMinutes(2));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();

        log.info("Jina embeddings: model={}, dimensions={}", model, dimensions);
    }

    @Override
    public List<Float> embed(String text, InputType inputType) {
        return embedBatch(List.of(text), inputType).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, InputType inputType) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        body.put("task", inputType == InputType.QUERY ? TASK_QUERY : TASK_PASSAGE);
        // Matryoshka truncation to match the vector(1024) column.
        body.put("dimensions", dimensions);

        String response;
        try {
            response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    // Both failure families, as always: transport errors have no status code,
                    // and only 429/5xx among status errors are worth retrying. A 401 or 422
                    // will never succeed on retry and must surface immediately.
                    .retryWhen(Retry.backoff(5, Duration.ofSeconds(3))
                            .maxBackoff(Duration.ofSeconds(60))
                            .filter(JinaEmbeddingClient::isRetryable)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();

        } catch (WebClientResponseException e) {
            throw new IllegalStateException(friendly(e), e);
        } catch (WebClientRequestException e) {
            throw new IllegalStateException("Could not reach the Jina API: " + e.getMessage(), e);
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
        String bodyText = e.getResponseBodyAsString();
        return switch (status) {
            case 401 -> "Jina rejected the API key (401). Check JINA_API_KEY and that the "
                    + "terminal/IDE was restarted after setting it.";
            case 402 -> "Jina reports the free token balance is exhausted (402). Body: " + bodyText
                    + " | Jina's free grant is roughly 1M tokens - far smaller than Voyage's "
                    + "200M. Either top up, or switch ai.embedding.provider (full re-index).";
            case 422 -> "Jina rejected the request (422): " + bodyText
                    + " | If this names the 'task' field, the accepted spelling may be "
                    + "nl2code_query/nl2code_document rather than nl2code.query/.passage - "
                    + "the docs show both forms. Adjust TASK_QUERY/TASK_PASSAGE.";
            case 429 -> "Jina rate limit (429). Body: " + bodyText
                    + " | Free keys allow 100 RPM / 100K TPM. Lower ai.embedding.batch-size "
                    + "or raise ai.embedding.pacing-millis.";
            default -> "Jina returned " + status + ": " + bodyText;
        };
    }

    private List<List<Float>> parse(String response) {
        JsonNode json;
        try {
            json = objectMapper.readTree(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Jina response: " + response, e);
        }

        // OpenAI-compatible shape: {"data":[{"embedding":[...],"index":0}], "usage":{...}}
        JsonNode data = json.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("Jina returned no embeddings. Response: " + response);
        }

        List<List<Float>> results = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new IllegalStateException("Jina item had no embedding array: " + item);
            }
            List<Float> vector = new ArrayList<>(embedding.size());
            for (JsonNode value : embedding) {
                vector.add(value.floatValue());
            }
            if (vector.size() != dimensions) {
                throw new IllegalStateException("Model '" + model + "' returned " + vector.size()
                        + "-dimension vectors, but the database column expects " + dimensions
                        + ". Check the 'dimensions' parameter is being honoured.");
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