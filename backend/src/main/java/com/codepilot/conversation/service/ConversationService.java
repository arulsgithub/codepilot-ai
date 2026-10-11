package com.codepilot.conversation.service;

import com.codepilot.auth.service.CurrentUserService;
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
    private final CurrentUserService currentUserService;

    public ConversationService(ConversationRepository conversationRepository,
                               CurrentUserService currentUserService) {
        this.conversationRepository = conversationRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public ConversationResponse createConversation(String title) {

        OffsetDateTime now = OffsetDateTime.now();

        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setUserId(currentUserService.requireUserId());
        conversation.setTitle(title);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        return toResponse(conversationRepository.save(conversation));
    }

    @Transactional
    public ConversationResponse editConversation(UUID conversationId, String newTitle) {
        Conversation conversation = require(conversationId);
        conversation.setTitle(newTitle);
        conversation.setUpdatedAt(OffsetDateTime.now());
        return toResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId) {
        return toResponse(require(conversationId));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getAllConversations() {
        return conversationRepository
                .findAllByUserIdOrderByUpdatedAtDesc(currentUserService.requireUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteConversation(UUID conversationId) {
        Conversation conversation = require(conversationId);
        conversationRepository.delete(conversation);
    }

    /**
     * The single ownership gate. Every read and write goes through it.
     *
     * The message deliberately says "not found" rather than "not yours". Distinguishing the
     * two tells an attacker which ids exist, which turns a guess into a confirmed target.
     */
    private Conversation require(UUID conversationId) {
        return conversationRepository
                .findByIdAndUserId(conversationId, currentUserService.requireUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
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