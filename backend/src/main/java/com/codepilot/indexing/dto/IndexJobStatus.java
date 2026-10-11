package com.codepilot.indexing.dto;

public enum IndexJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}