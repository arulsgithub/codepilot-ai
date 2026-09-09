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

    /**
     * The response format is strict because EditBlockParser matches on these exact markers,
     * and searchText must be byte-identical to the file. The prohibitions below are all
     * failure modes models fall into by default - especially eliding code with "...".
     */
    public String buildEditSystemPrompt(List<CodeChunkEntity> relevantChunks) {
        StringBuilder contextBlock = new StringBuilder();
        for (CodeChunkEntity chunk : relevantChunks) {
            contextBlock.append("File: ").append(chunk.getRelativeFilePath())
                    .append(" (lines ").append(chunk.getStartLine())
                    .append("-").append(chunk.getEndLine()).append(")\n")
                    .append("```\n").append(chunk.getContent()).append("\n```\n\n");
        }

        return """
                You are CodePilot, an AI software engineering assistant that edits code.

                Produce edits ONLY in the following format, and nothing else outside it:

                SUMMARY: <one line describing the overall change>

                ### EDIT: <relative/file/path.java>
                <<<<<<< SEARCH
                <exact existing code to find>
                =======
                <replacement code>
                >>>>>>> REPLACE

                Rules you MUST follow:
                - The SEARCH text must be copied EXACTLY from the code shown below, character
                  for character, including whitespace and indentation.
                - NEVER abbreviate with "...", "// rest unchanged", or similar. Every line in
                  the SEARCH block must be real, complete code from the file.
                - The SEARCH text must be unique within its file. Include enough surrounding
                  lines to make it unambiguous.
                - Use one ### EDIT block per change. Multiple blocks may target the same file.
                - Only edit files shown in the context below. If the context does not contain
                  what you need, say so in the SUMMARY and produce no EDIT blocks.
                - Do not explain your reasoning outside the SUMMARY line.

                ==== RELEVANT CODE ====
                """ + contextBlock;
    }

}