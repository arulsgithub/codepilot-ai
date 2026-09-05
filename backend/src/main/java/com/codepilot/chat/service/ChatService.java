package com.codepilot.chat.service;

import com.codepilot.ai.orchestrator.AIOrchestrator;
import com.codepilot.chat.dto.ChatRequest;
import com.codepilot.chat.dto.ChatResponse;
import com.codepilot.chat.dto.StreamEvent;
import com.codepilot.message.dto.MessageResponse;
import com.codepilot.message.entity.Message;
import com.codepilot.message.service.MessageService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
public class ChatService {

    private final MessageService messageService;
    private final AIOrchestrator aiOrchestrator;

    public ChatService(
            MessageService messageService,
            AIOrchestrator aiOrchestrator) {

        this.messageService = messageService;
        this.aiOrchestrator = aiOrchestrator;
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
        String aiResponse =
                aiOrchestrator.generateResponse(history);

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
            tokens = aiOrchestrator.generateStreamingResponse(history);
        } catch (Exception exception) {
            return Flux.just(
                    new StreamEvent("START", null),
                    new StreamEvent("ERROR", null)
            );
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
