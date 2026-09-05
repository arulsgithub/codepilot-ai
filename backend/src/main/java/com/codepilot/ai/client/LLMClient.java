package com.codepilot.ai.client;

import com.codepilot.ai.dto.LLMRequest;
import com.codepilot.ai.dto.LLMResponse;
import reactor.core.publisher.Flux;

public interface LLMClient {

    LLMResponse chat(LLMRequest request);
    Flux<String> streamChat(LLMRequest request);

}