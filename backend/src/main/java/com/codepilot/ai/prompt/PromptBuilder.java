package com.codepilot.ai.prompt;

import org.springframework.stereotype.Component;
import com.codepilot.indexing.entity.CodeChunkEntity;
import java.util.List;

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

    /**
     * Same base instructions as buildSystemPrompt(), plus the retrieved code chunks appended
     * as labelled context blocks. Telling the model explicitly to prefer this context over
     * guessing is important - without that instruction, models often ignore provided context
     * and answer from general knowledge anyway.
     */
    public String buildSystemPromptWithContext(List<CodeChunkEntity> relevantChunks) {

        StringBuilder contextBlock = new StringBuilder();
        for (CodeChunkEntity chunk : relevantChunks) {
            contextBlock.append("File: ").append(chunk.getRelativeFilePath())
                    .append(" (").append(chunk.getQualifiedName()).append(", lines ")
                    .append(chunk.getStartLine()).append("-").append(chunk.getEndLine()).append(")\n")
                    .append("```java\n").append(chunk.getContent()).append("\n```\n\n");
        }

        return buildSystemPrompt() + """

                You have been given relevant excerpts from the user's actual codebase below.
                Use this real code as your primary source of truth when it's relevant to the
                question. If the provided excerpts don't contain what's needed to answer,
                say so explicitly rather than guessing.

                ==== RELEVANT CODE ====
                """ + contextBlock;
    }

}