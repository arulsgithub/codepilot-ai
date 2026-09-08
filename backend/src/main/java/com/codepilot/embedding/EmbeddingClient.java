package com.codepilot.embedding;

import java.util.List;

public interface EmbeddingClient {

    enum InputType {
        QUERY,   // embedding a user's live question, for searching
        PASSAGE  // embedding code/documents, for storing and later being searched against
    }

    List<Float> embed(String text, InputType inputType);
    List<List<Float>> embedBatch(List<String> texts, InputType inputType);
    int dimensions();
}