package com.codepilot.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ModelMode {
    FAST,
    CODE,
    RAG,
    REASONING,
    TITLE;

    /**
     * Lenient JSON binding: "" and null both mean "no explicit mode" (so the app picks),
     * and matching is case-insensitive. Without this, a blank form field from the frontend
     * causes a 500 at deserialization before the controller is even reached.
     */
    @JsonCreator
    public static ModelMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ModelMode.valueOf(value.trim().toUpperCase());
    }
}