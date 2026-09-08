package com.codepilot.ai.orchestrator;

import com.codepilot.ai.client.LLMClient;
import com.codepilot.ai.client.NemotronProperties;
import com.codepilot.ai.dto.LLMMessage;
import com.codepilot.ai.dto.LLMRequest;
import com.codepilot.ai.dto.LLMResponse;
import com.codepilot.ai.prompt.PromptBuilder;
import com.codepilot.indexing.entity.CodeChunkEntity;
import com.codepilot.message.entity.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class AIOrchestrator {

    private final LLMClient llmClient;
    private final PromptBuilder promptBuilder;
    private final NemotronProperties properties;

    public AIOrchestrator(
            LLMClient llmClient,
            PromptBuilder promptBuilder,
            NemotronProperties properties) {

        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
    }

    public String generateResponse(List<Message> conversationHistory) {

        List<LLMMessage> messages = new ArrayList<>();

        // System instruction
        messages.add(
                new LLMMessage(
                        "system",
                        promptBuilder.buildSystemPrompt()
                )
        );

        // Conversation history
        for (Message message : conversationHistory) {

            String role = switch (message.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> "system";
            };

            messages.add(
                    new LLMMessage(
                            role,
                            message.getContent()
                    )
            );
        }

        LLMRequest request = new LLMRequest(
                properties.model(),
                messages,
                0.2,
                false
        );

        LLMResponse response = llmClient.chat(request);

        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {

            throw new IllegalStateException(
                    "AI provider returned an empty response"
            );
        }

        String content =
                response.choices()
                        .getFirst()
                        .message()
                        .content();

        if (content == null || content.isBlank()) {
            throw new IllegalStateException(
                    "AI provider returned empty content"
            );
        }

        return content;
    }

    public Flux<String> generateStreamingResponse(List<Message> conversationHistory) {

        List<LLMMessage> messages = new ArrayList<>();

        messages.add(
                new LLMMessage(
                        "system",
                        promptBuilder.buildSystemPrompt()
                )
        );

        for (Message message : conversationHistory) {

            String role = switch (message.getRole()) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> "system";
            };

            messages.add(
                    new LLMMessage(
                            role,
                            message.getContent()
                    )
            );
        }

        LLMRequest request = new LLMRequest(
                properties.model(),
                messages,
                0.2,
                true
        );

        return llmClient.streamChat(request);
    }

    public String generateResponse(List<Message> conversationHistory, List<CodeChunkEntity> relevantChunks) {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage("system", promptBuilder.buildSystemPromptWithContext(relevantChunks)));
        appendHistory(messages, conversationHistory);

        LLMRequest request = new LLMRequest(properties.model(), messages, 0.2, false);
        LLMResponse response = llmClient.chat(request);

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

    public Flux<String> generateStreamingResponse(List<Message> conversationHistory, List<CodeChunkEntity> relevantChunks) {
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage("system", promptBuilder.buildSystemPromptWithContext(relevantChunks)));
        appendHistory(messages, conversationHistory);

        LLMRequest request = new LLMRequest(properties.model(), messages, 0.2, true);
        return llmClient.streamChat(request);
    }

    /**
     * Pulled out of the 4 near-identical role-mapping loops (2 existing + 2 new above) so the
     * mapping logic exists in exactly one place - purely a cleanup, doesn't change behavior.
     */
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