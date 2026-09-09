package com.codepilot.ai.orchestrator;

import com.codepilot.ai.dto.LLMMessage;
import com.codepilot.ai.dto.LLMRequest;
import com.codepilot.ai.dto.LLMResponse;
import com.codepilot.ai.model.ModelMode;
import com.codepilot.ai.model.ModelRouter;
import com.codepilot.ai.model.ResolvedModel;
import com.codepilot.ai.prompt.PromptBuilder;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.message.entity.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class AIOrchestrator {

    private final ModelRouter modelRouter;
    private final PromptBuilder promptBuilder;

    public AIOrchestrator(ModelRouter modelRouter, PromptBuilder promptBuilder) {
        this.modelRouter = modelRouter;
        this.promptBuilder = promptBuilder;
    }

    // ---- Non-RAG ----

    public String generateResponse(List<Message> conversationHistory, ModelMode mode) {
        return complete(buildMessages(conversationHistory, null), mode);
    }

    public Flux<String> generateStreamingResponse(List<Message> conversationHistory, ModelMode mode) {
        return stream(buildMessages(conversationHistory, null), mode);
    }

    // ---- RAG (retrieved code chunks injected into the system prompt) ----

    public String generateResponse(List<Message> conversationHistory,
                                   List<CodeChunkEntity> relevantChunks, ModelMode mode) {
        return complete(buildMessages(conversationHistory, relevantChunks), mode);
    }

    public Flux<String> generateStreamingResponse(List<Message> conversationHistory,
                                                  List<CodeChunkEntity> relevantChunks, ModelMode mode) {
        return stream(buildMessages(conversationHistory, relevantChunks), mode);
    }

    // ---- shared internals ----

    /** relevantChunks == null means "no retrieval context" (plain chat). */
    private List<LLMMessage> buildMessages(List<Message> conversationHistory,
                                           List<CodeChunkEntity> relevantChunks) {
        List<LLMMessage> messages = new ArrayList<>();
        String systemPrompt = (relevantChunks == null)
                ? promptBuilder.buildSystemPrompt()
                : promptBuilder.buildSystemPromptWithContext(relevantChunks);
        messages.add(new LLMMessage("system", systemPrompt));
        appendHistory(messages, conversationHistory);
        return messages;
    }

    private String complete(List<LLMMessage> messages, ModelMode mode) {
        ResolvedModel resolved = modelRouter.resolve(mode);
        LLMRequest request = new LLMRequest(resolved.model(), messages, 0.2, false);
        LLMResponse response = resolved.client().chat(request);

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new IllegalStateException("AI provider returned an empty response");
        }
        String content = response.choices().getFirst().message().content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI provider returned empty content");
        }
        return content;
    }

    private Flux<String> stream(List<LLMMessage> messages, ModelMode mode) {
        ResolvedModel resolved = modelRouter.resolve(mode);
        LLMRequest request = new LLMRequest(resolved.model(), messages, 0.2, true);
        return resolved.client().streamChat(request);
    }

    private void appendHistory(List<LLMMessage> messages, List<Message> conversationHistory) {
        for (Message message : conversationHistory) {
            String role = switch (message.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> "system";
            };
            messages.add(new LLMMessage(role, message.getContent()));
        }
    }
}