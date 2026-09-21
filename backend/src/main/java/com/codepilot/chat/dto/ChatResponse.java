package com.codepilot.chat.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        UUID userMessageId,
        UUID assistantMessageId,
        String response,

        /** Empty when the answer used no repository context. */
        List<SourceReference> sources,

        /**
         * How many chunks were actually fed to the model.
         *
         * Reported to the CLIENT, not just the log, on purpose. A confident answer with
         * contextChunkCount == 0 is the signature of RAG silently not running, and whoever
         * is looking at the response should be able to see that without server access.
         */
        int contextChunkCount,

        /**
         * True when a repository WAS attached but retrieval returned nothing, so the answer
         * comes from the model's general knowledge alone.
         *
         * This is the honest middle path. Refusing to answer would break legitimate broad
         * questions ("what does this project do?"); answering silently is what produced a
         * fabricated explanation of a class written the day before. So: answer, but say so.
         */
        boolean answeredWithoutContext
) {
}