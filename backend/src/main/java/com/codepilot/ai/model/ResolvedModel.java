package com.codepilot.ai.model;

import com.codepilot.ai.client.LLMClient;

/** The outcome of routing: which client to call, and which model name to send it. */
public record ResolvedModel(LLMClient client, String model) {
}