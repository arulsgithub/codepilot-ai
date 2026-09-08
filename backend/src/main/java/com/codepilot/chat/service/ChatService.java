package com.codepilot.chat.service;

import com.codepilot.ai.orchestrator.AIOrchestrator;
import com.codepilot.chat.dto.ChatRequest;
import com.codepilot.chat.dto.ChatResponse;
import com.codepilot.chat.dto.StreamEvent;
import com.codepilot.message.dto.MessageResponse;
import com.codepilot.message.entity.Message;
import com.codepilot.message.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.codepilot.retrieval.service.RetrievalService;

import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final MessageService messageService;
    private final AIOrchestrator aiOrchestrator;

    private final RetrievalService retrievalService;

    public ChatService(MessageService messageService, AIOrchestrator aiOrchestrator, RetrievalService retrievalService) {
        this.messageService = messageService;
        this.aiOrchestrator = aiOrchestrator;
        this.retrievalService = retrievalService;
    }

    public ChatResponse chat(ChatRequest request) {

        // 1. Save user's message
        MessageResponse userMessage =
                messageService.createUserMessage(
                        request.conversationId(),
                        request.message(),
                        request.requestId()
                );

        // 2. Load conversation history
        List<Message> history =
                messageService.getMessageEntities(
                        request.conversationId()
                );

        // 3. Send conversation to Nemotron
        String aiResponse = (request.repositoryRoot() != null)
                ? aiOrchestrator.generateResponse(history, retrievalService.retrieveRelevantChunks(request.message(), request.repositoryRoot()))
                : aiOrchestrator.generateResponse(history);

        // 4. Save assistant's response
        MessageResponse assistantMessage =
                messageService.createAssistantMessage(
                        request.conversationId(),
                        aiResponse,
                        request.requestId()
                );

        // 5. Return response
        return new ChatResponse(
                request.conversationId(),
                userMessage.id(),
                assistantMessage.id(),
                aiResponse
        );
    }

    public Flux<StreamEvent> streamChat(ChatRequest request) {

        // Run the whole flow at subscribe time so that a failure in the
        // synchronous prologue (missing conversation, DB error, ...) surfaces
        // as an onError signal rather than a thrown exception. A thrown
        // exception here would be routed to GlobalExceptionHandler, which
        // cannot serialise ApiErrorResponse onto a text/event-stream response
        // (HttpMessageNotWritableException) and leaves the client with a
        // broken, tokenless response.
        return Flux.defer(() -> buildStream(request))
                .onErrorResume(error -> {
                    log.error(
                            "Streaming chat failed for conversation {}",
                            request.conversationId(),
                            error
                    );
                    return Flux.just(
                            new StreamEvent("START", null),
                            new StreamEvent("ERROR", null)
                    );
                });
    }

    private Flux<StreamEvent> buildStream(ChatRequest request) {

        MessageResponse userMessage =
                messageService.createUserMessage(
                        request.conversationId(),
                        request.message(),
                        request.requestId()
                );

        MessageResponse completedAssistant = messageService.findAssistantMessageForRequest(
                request.conversationId(),
                request.requestId()
        );

        // A retry after the server completed but the browser lost the final
        // event replays the stored answer instead of invoking the model again.
        if (completedAssistant != null) {
            return Flux.just(
                    new StreamEvent("START", null),
                    new StreamEvent("TOKEN", completedAssistant.content()),
                    new StreamEvent("COMPLETE", null)
            );
        }

        List<Message> history =
                messageService.getMessageEntities(
                        request.conversationId()
                );

        Flux<String> tokens;
        try {
            tokens = (request.repositoryRoot() != null)
                    ? aiOrchestrator.generateStreamingResponse(history, retrievalService.retrieveRelevantChunks(request.message(), request.repositoryRoot()))
                    : aiOrchestrator.generateStreamingResponse(history);
        } catch (Exception exception) {
            return Flux.just(new StreamEvent("START", null), new StreamEvent("ERROR", null));
        }

        StringBuilder answer = new StringBuilder();

        return Flux.concat(
                Flux.just(new StreamEvent("START", null)),

                tokens
                        .doOnNext(answer::append)
                        .map(token -> new StreamEvent("TOKEN", token))
                        .concatWith(Mono.fromCallable(() -> {
                                    messageService.createAssistantMessage(
                                            request.conversationId(),
                                            answer.toString(),
                                            request.requestId()
                                    );
                                    return new StreamEvent("COMPLETE", null);
                                })
                                .subscribeOn(Schedulers.boundedElastic()))
                        .onErrorResume(exception -> Flux.just(new StreamEvent("ERROR", null)))
        );
    }
}
