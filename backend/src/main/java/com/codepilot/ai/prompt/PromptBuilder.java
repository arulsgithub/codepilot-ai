package com.codepilot.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildSystemPrompt() {

        return """
                You are CodePilot, an AI software engineering assistant.

                Help developers understand, debug, design, and improve software.

                Be technically accurate and concise.
                If you are uncertain, clearly state the uncertainty.
                Do not invent code, files, APIs, or system behavior.
                """;
    }
}