package com.codepilot.message.controller;

import com.codepilot.message.dto.CreateMessageRequest;
import com.codepilot.message.dto.MessageResponse;
import com.codepilot.message.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> createMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody CreateMessageRequest request) {

        MessageResponse response =
                messageService.createMessage(
                        conversationId,
                        request.role(),
                        request.content()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable UUID conversationId) {

        return ResponseEntity.ok(
                messageService.getMessages(conversationId)
        );
    }
}