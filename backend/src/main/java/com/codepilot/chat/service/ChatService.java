package com.codepilot.chat.service;

import com.codepilot.ai.model.ModelMode;
import com.codepilot.ai.orchestrator.AIOrchestrator;
import com.codepilot.chat.dto.ChatRequest;
import com.codepilot.chat.dto.ChatResponse;
import com.codepilot.chat.dto.SourceReference;
import com.codepilot.chat.dto.StreamEvent;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.message.dto.MessageResponse;
import com.codepilot.message.entity.Message;
import com.codepilot.message.service.MessageService;
import com.codepilot.retrieval.service.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final MessageService messageService;
    private final AIOrchestrator aiOrchestrator;
    private final RetrievalService retrievalService;

    public ChatService(MessageService messageService, AIOrchestrator aiOrchestrator,
                       RetrievalService retrievalService) {
        this.messageService = messageService;
        this.aiOrchestrator = aiOrchestrator;
        this.retrievalService = retrievalService;
    }

    public ChatResponse chat(ChatRequest request) {

        MessageResponse userMessage = messageService.createUserMessage(
                request.conversationId(), request.message(), request.requestId());

        List<Message> history = messageService.getMessageEntities(request.conversationId());

        ModelMode mode = resolveMode(request);

        // Retrieve ONCE into a variable (previously this was buried inside the ternary,
        // so the chunks were discarded and could never be reported back to the client).
        List<CodeChunkEntity> chunks = hasRepository(request)
                ? retrievalService.retrieveRelevantChunks(request.message(), request.repositoryRoot())
                : List.of();

        String aiResponse = chunks.isEmpty()
                ? aiOrchestrator.generateResponse(history, mode)
                : aiOrchestrator.generateResponse(history, chunks, mode);

        MessageResponse assistantMessage = messageService.createAssistantMessage(
                request.conversationId(), aiResponse, request.requestId());

        return new ChatResponse(
                request.conversationId(),
                userMessage.id(),
                assistantMessage.id(),
                aiResponse,
                toSourceReferences(chunks)
        );
    }

    public Flux<StreamEvent> streamChat(ChatRequest request) {
        return Flux.defer(() -> buildStream(request))
                .onErrorResume(error -> {
                    log.error("Streaming chat failed for conversation {}",
                            request.conversationId(), error);
                    return Flux.just(
                            new StreamEvent("START", null),
                            new StreamEvent("ERROR", null)
                    );
                });
    }

    private Flux<StreamEvent> buildStream(ChatRequest request) {

        MessageResponse userMessage = messageService.createUserMessage(
                request.conversationId(), request.message(), request.requestId());

        MessageResponse completedAssistant = messageService.findAssistantMessageForRequest(
                request.conversationId(), request.requestId());

        // Replay path: the answer already completed but the browser lost the final event.
        // Note: sources are NOT replayed - they aren't persisted with the message, so a
        // replayed answer arrives without them. See the limitation note below.
        if (completedAssistant != null) {
            return Flux.just(
                    new StreamEvent("START", null),
                    new StreamEvent("TOKEN", completedAssistant.content()),
                    new StreamEvent("COMPLETE", null)
            );
        }

        List<Message> history = messageService.getMessageEntities(request.conversationId());
        ModelMode mode = resolveMode(request);

        List<CodeChunkEntity> chunks;
        Flux<String> tokens;
        try {
            chunks = hasRepository(request)
                    ? retrievalService.retrieveRelevantChunks(request.message(), request.repositoryRoot())
                    : List.of();

            tokens = chunks.isEmpty()
                    ? aiOrchestrator.generateStreamingResponse(history, mode)
                    : aiOrchestrator.generateStreamingResponse(history, chunks, mode);
        } catch (Exception exception) {
            return Flux.just(new StreamEvent("START", null), new StreamEvent("ERROR", null));
        }

        StringBuilder answer = new StringBuilder();

        // SOURCES is emitted immediately after START (before any token) so the UI can show
        // "reading from these files" while the answer streams in. Omitted entirely when
        // there was no retrieval, so plain chat's event sequence is byte-identical to before.
        List<SourceReference> sources = toSourceReferences(chunks);
        Flux<StreamEvent> prologue = sources.isEmpty()
                ? Flux.just(new StreamEvent("START", null))
                : Flux.just(new StreamEvent("START", null),
                new StreamEvent("SOURCES", null, sources));

        return Flux.concat(
                prologue,
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

    /**
     * Maps internal chunks to the client-facing DTO, deduplicated by qualified name.
     * A large class can be split across several chunks (chunkIndex 0,1,2...) which would
     * otherwise show the user the same method three times; we keep the first occurrence
     * and widen its line range to cover the others.
     */
    private List<SourceReference> toSourceReferences(List<CodeChunkEntity> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Map<String, SourceReference> byName = new LinkedHashMap<>(); // preserves relevance order
        for (CodeChunkEntity chunk : chunks) {
            byName.merge(
                    chunk.getQualifiedName(),
                    new SourceReference(chunk.getRelativeFilePath(), chunk.getQualifiedName(),
                            chunk.getStartLine(), chunk.getEndLine()),
                    (existing, incoming) -> new SourceReference(
                            existing.filePath(),
                            existing.qualifiedName(),
                            Math.min(existing.startLine(), incoming.startLine()),
                            Math.max(existing.endLine(), incoming.endLine()))
            );
        }
        return new ArrayList<>(byName.values());
    }

    private ModelMode resolveMode(ChatRequest request) {
        if (request.mode() != null) {
            return request.mode();
        }
        return hasRepository(request) ? ModelMode.RAG : ModelMode.CODE;
    }

    /** Blank and null both mean "no repository attached" - a blank form field is not a repo. */
    private boolean hasRepository(ChatRequest request) {
        return request.repositoryRoot() != null && !request.repositoryRoot().isBlank();
    }
}