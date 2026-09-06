package com.codepilot.conversation.controller;

import com.codepilot.conversation.dto.ConversationResponse;
import com.codepilot.conversation.dto.CreateConversationRequest;
import com.codepilot.conversation.dto.UpdateConversationRequest;
import com.codepilot.conversation.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {

        ConversationResponse response =
                conversationService.createConversation(request.title());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getConversations() {

        return ResponseEntity.ok(
                conversationService.getAllConversations()
        );
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> getConversation(
            @PathVariable UUID conversationId) {

        return ResponseEntity.ok(
                conversationService.getConversation(conversationId)
        );
    }

    @PatchMapping("/{conversationId}")
    public ResponseEntity<ConversationResponse> updateConversation(
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationRequest request) {

        return ResponseEntity.ok(
                conversationService.editConversation(conversationId, request.title())
        );
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable UUID conversationId) {

        conversationService.deleteConversation(conversationId);

        return ResponseEntity.noContent().build();
    }
}