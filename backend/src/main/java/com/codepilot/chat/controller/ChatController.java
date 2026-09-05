package com.codepilot.chat.controller;

import com.codepilot.chat.dto.ChatRequest;
import com.codepilot.chat.dto.ChatResponse;
import com.codepilot.chat.dto.StreamEvent;
import com.codepilot.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request) {

        ChatResponse response = chatService.chat(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<StreamEvent> streamChat(
            @Valid @RequestBody ChatRequest request) {

        return chatService.streamChat(request);
    }
}