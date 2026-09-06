package com.codepilot.message.service;

import com.codepilot.conversation.entity.Conversation;
import com.codepilot.conversation.repository.ConversationRepository;
import com.codepilot.message.dto.MessageResponse;
import com.codepilot.message.entity.Message;
import com.codepilot.message.entity.MessageRole;
import com.codepilot.message.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    /** Longest auto-generated conversation title, in characters. */
    private static final int MAX_TITLE_LENGTH = 60;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository) {

        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public MessageResponse createMessage(
            UUID conversationId,
            MessageRole role,
            String content) {

        return createMessage(conversationId, role, content, null);
    }

    @Transactional
    public MessageResponse createMessage(
            UUID conversationId,
            MessageRole role,
            String content,
            UUID clientRequestId) {

        Conversation conversation =
                conversationRepository.findByIdForUpdate(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found: " + conversationId
                                ));

        conversation.setUpdatedAt(OffsetDateTime.now());

        Integer sequenceNumber =
                messageRepository.findMaxSequenceNumberByConversation(conversation) + 1;

        Message message = new Message();

        message.setId(UUID.randomUUID());
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setSequenceNumber(sequenceNumber);

        // Derive a readable, deterministic title from the first message of the
        // conversation. `conversation` is a managed entity here, so the change
        // flushes with this transaction — no extra service call or Nemotron
        // round-trip. A later manual rename (PATCH) is never overwritten
        // because sequence number 1 only ever occurs once.
        if (sequenceNumber == 1) {
            conversation.setTitle(deriveTitle(content));
        }

        message.setCreatedAt(OffsetDateTime.now());
        message.setClientRequestId(clientRequestId);

        Message savedMessage = messageRepository.save(message);

        return toResponse(savedMessage);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(UUID conversationId) {

        Conversation conversation =
                conversationRepository.findById(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found: " + conversationId
                                ));

        return messageRepository
                .findByConversationOrderBySequenceNumberAsc(conversation)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Turns the first user message into a compact single-line title:
     * whitespace collapsed, trimmed to {@link #MAX_TITLE_LENGTH} characters
     * with an ellipsis when it overflows.
     */
    private String deriveTitle(String firstMessage) {
        String normalized = firstMessage.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "New Conversation";
        }
        if (normalized.length() <= MAX_TITLE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_TITLE_LENGTH).stripTrailing() + "…";
    }

    private MessageResponse toResponse(Message message) {

        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getRole(),
                message.getContent(),
                message.getSequenceNumber(),
                message.getCreatedAt()
        );
    }

    @Transactional
    public MessageResponse createUserMessage(
            UUID conversationId,
            String content,
            UUID requestId) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId
                ));

        return messageRepository
                .findByConversationAndClientRequestIdAndRole(conversation, requestId, MessageRole.USER)
                .map(this::toResponse)
                .orElseGet(() -> createMessage(
                        conversationId,
                        MessageRole.USER,
                        content,
                        requestId
                ));
    }

    public MessageResponse createUserMessage(
            UUID conversationId,
            String content) {

        return createMessage(conversationId, MessageRole.USER, content);
    }

    @Transactional
    public MessageResponse createAssistantMessage(
            UUID conversationId,
            String content,
            UUID requestId) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId
                ));

        Message existing = messageRepository
                .findByConversationAndClientRequestIdAndRole(conversation, requestId, MessageRole.ASSISTANT)
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        return createMessage(
                conversationId,
                MessageRole.ASSISTANT,
                content,
                requestId
        );
    }

    @Transactional(readOnly = true)
    public MessageResponse findAssistantMessageForRequest(
            UUID conversationId,
            UUID requestId) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId
                ));

        return messageRepository
                .findByConversationAndClientRequestIdAndRole(conversation, requestId, MessageRole.ASSISTANT)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Message> getMessageEntities(UUID conversationId) {

        Conversation conversation =
                conversationRepository.findById(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found: " + conversationId
                                ));

        return messageRepository
                .findByConversationOrderBySequenceNumberAsc(conversation);
    }

}
