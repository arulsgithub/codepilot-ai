package com.codepilot.conversation.service;

import com.codepilot.conversation.dto.ConversationResponse;
import com.codepilot.conversation.entity.Conversation;
import com.codepilot.conversation.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Transactional
    public ConversationResponse createConversation(String title) {

        OffsetDateTime now = OffsetDateTime.now();

        Conversation conversation = new Conversation();

        conversation.setId(UUID.randomUUID());
        conversation.setTitle(title);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        Conversation savedConversation =
                conversationRepository.save(conversation);

        return toResponse(savedConversation);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Conversation not found: " + conversationId
                        )
                );

        return toResponse(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getAllConversations() {

        return conversationRepository
                .findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteConversation(UUID conversationId) {

        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException(
                    "Conversation not found: " + conversationId
            );
        }

        conversationRepository.deleteById(conversationId);
    }

    private ConversationResponse toResponse(Conversation conversation) {

        return new ConversationResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}