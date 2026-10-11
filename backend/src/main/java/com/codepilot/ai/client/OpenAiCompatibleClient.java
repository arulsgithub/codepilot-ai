package com.codepilot.ai.client;

import com.codepilot.ai.dto.LLMRequest;
import com.codepilot.ai.dto.LLMResponse;
import com.codepilot.ai.dto.LLMStreamChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Objects;

/**
 * One client for any OpenAI-compatible provider (Groq, OpenRouter, ...). Not a @Component -
 * ModelRouter instantiates one per configured provider, since each needs its own base URL/key.
 *
 * This intentionally duplicates NemotronClient's logic rather than refactoring it, so the
 * working Nemotron path carries zero risk from this change.
 */
public class OpenAiCompatibleClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

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
        try {
            return webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LLMResponse.class)
                    // Free tiers are capped per MINUTE, so the backoff has to be able to outlast
                    // a window - 4 attempts topping out at 10s cannot. Both failure families are
                    // covered: transport errors carry no status code, and among status errors
                    // only 429/5xx are worth retrying (a 401 never succeeds on retry).
                    .retryWhen(Retry.backoff(4, Duration.ofSeconds(4))
                            .maxBackoff(Duration.ofSeconds(45))
                            .filter(OpenAiCompatibleClient::isRetryable)
                            .doBeforeRetry(signal -> log.warn(
                                    "{} request failed ({}), retry {} of 4",
                                    providerName,
                                    rootMessage(signal.failure()),
                                    signal.totalRetries() + 1))
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();

        } catch (WebClientResponseException e) {
            throw new IllegalStateException(friendly(e), e);
        }
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
                .flatMap(this::extractContent)
                // NOTE: deliberately NO retry here.
                //
                // retryWhen resubscribes, which restarts the request from scratch - but tokens
                // already emitted have gone to the browser. The user would see the first half of
                // one answer followed by a complete second answer. A rate limit that arrives
                // before the first token is the only safely retryable case, and Reactor gives us
                // no clean way to distinguish it. Failing with a clear message beats corrupting
                // the output.
                .onErrorMap(WebClientResponseException.class,
                        e -> new IllegalStateException(friendly(e), e));
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
        String body = e.getResponseBodyAsString();

        return switch (status) {
            case 401 -> providerName + " rejected the API key (401). Check the key for this "
                    + "provider and that the terminal/IDE was restarted after setting it.";
            case 402 -> providerName + " reports no credit (402): " + body;
            case 429 -> {
                // Providers put the wait time in the body ("try again in 12.4s") and often in a
                // Retry-After header. Surfacing it turns "try later" into "try in 13 seconds".
                String retryAfter = e.getHeaders().getFirst("Retry-After");
                yield providerName + " rate limit (429)"
                        + (retryAfter == null ? "" : ", retry after " + retryAfter + "s")
                        + ". Body: " + body
                        + " | Free tiers cap TOKENS per minute, and each RAG prompt carries several "
                        + "code chunks. Reduce the retrieval top-K or the per-chunk size to fit "
                        + "more calls into the budget.";
            }
            default -> providerName + " returned " + status + ": " + body;
        };
    }

    private String rootMessage(Throwable t) {
        if (t instanceof WebClientResponseException response) {
            return response.getStatusCode().toString();
        }
        return t.getClass().getSimpleName();
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