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
import com.codepilot.repo.service.RepositoryPathResolver;
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
    private final RepositoryPathResolver pathResolver;

    public ChatService(MessageService messageService, AIOrchestrator aiOrchestrator,
                       RetrievalService retrievalService, RepositoryPathResolver pathResolver) {
        this.messageService = messageService;
        this.aiOrchestrator = aiOrchestrator;
        this.retrievalService = retrievalService;
        this.pathResolver = pathResolver;
    }

    public ChatResponse chat(ChatRequest request) {

        MessageResponse userMessage = messageService.createUserMessage(
                request.conversationId(), request.message(), request.requestId());

        List<Message> history = messageService.getMessageEntities(request.conversationId());

        ModelMode mode = resolveMode(request);

        // Resolve ONCE, through the shared chokepoint. A repositoryId and a raw repositoryRoot
        // must canonicalize to the same string, or they address different rows in code_chunks
        // and one of them silently finds nothing.
        String repositoryRoot = resolveRepositoryRoot(request);

        List<CodeChunkEntity> chunks = repositoryRoot == null
                ? List.of()
                : retrievalService.retrieveRelevantChunks(request.message(), repositoryRoot);

        String aiResponse = chunks.isEmpty()
                ? aiOrchestrator.generateResponse(history, mode)
                : aiOrchestrator.generateResponse(history, chunks, mode);

        // A repository was asked for but nothing came back: the answer is general knowledge.
        // Say so in the response rather than letting it pass as repository-grounded.
        boolean answeredWithoutContext = repositoryRoot != null && chunks.isEmpty();
        if (answeredWithoutContext) {
            log.warn("Answered WITHOUT repository context despite a repository being attached "
                            + "(conversation {}). The answer is from general knowledge only.",
                    request.conversationId());
        }

        MessageResponse assistantMessage = messageService.createAssistantMessage(
                request.conversationId(), aiResponse, request.requestId());

        return new ChatResponse(
                request.conversationId(),
                userMessage.id(),
                assistantMessage.id(),
                aiResponse,
                toSourceReferences(chunks),
                chunks.size(),
                answeredWithoutContext
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
        // replayed answer arrives without them.
        if (completedAssistant != null) {
            return Flux.just(
                    new StreamEvent("START", null),
                    new StreamEvent("TOKEN", completedAssistant.content()),
                    new StreamEvent("COMPLETE", null)
            );
        }

        List<Message> history = messageService.getMessageEntities(request.conversationId());
        ModelMode mode = resolveMode(request);
        String repositoryRoot = resolveRepositoryRoot(request);

        List<CodeChunkEntity> chunks;
        Flux<String> tokens;
        try {
            chunks = repositoryRoot == null
                    ? List.of()
                    : retrievalService.retrieveRelevantChunks(request.message(), repositoryRoot);

            tokens = chunks.isEmpty()
                    ? aiOrchestrator.generateStreamingResponse(history, mode)
                    : aiOrchestrator.generateStreamingResponse(history, chunks, mode);
        } catch (Exception exception) {
            log.error("Retrieval or generation failed for conversation {}",
                    request.conversationId(), exception);
            return Flux.just(new StreamEvent("START", null), new StreamEvent("ERROR", null));
        }

        if (repositoryRoot != null && chunks.isEmpty()) {
            log.warn("Streaming answer WITHOUT repository context despite a repository being "
                    + "attached (conversation {})", request.conversationId());
        }

        StringBuilder answer = new StringBuilder();

        // SOURCES goes out immediately after START so the UI can show "reading from these files"
        // while the answer streams.
        //
        // Changed: when a repository IS attached we now emit SOURCES even when it is EMPTY.
        // An empty list is exactly the signal the UI needs to warn "answered without repository
        // context" - omitting the event would make that case indistinguishable from plain chat,
        // which is precisely the ambiguity that let a fabricated answer through unnoticed.
        // With no repository attached the event is still omitted entirely, so plain chat's
        // event sequence is byte-identical to before.
        List<SourceReference> sources = toSourceReferences(chunks);
        Flux<StreamEvent> prologue = repositoryRoot == null
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
     * Turn whichever repository identifier the caller sent into the canonical path, or null
     * when no repository was attached at all.
     *
     * A bad id or path throws IllegalArgumentException from the resolver, which the global
     * handler turns into a 400. That is deliberate: "you named a repository that does not
     * exist" is an error worth surfacing, not something to quietly degrade into a
     * context-free answer.
     */
    private String resolveRepositoryRoot(ChatRequest request) {
        boolean attached = request.repositoryId() != null
                || (request.repositoryRoot() != null && !request.repositoryRoot().isBlank());

        return attached
                ? pathResolver.resolve(request.repositoryId(), request.repositoryRoot())
                : null;
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
        boolean attached = request.repositoryId() != null
                || (request.repositoryRoot() != null && !request.repositoryRoot().isBlank());
        return attached ? ModelMode.RAG : ModelMode.CODE;
    }
}