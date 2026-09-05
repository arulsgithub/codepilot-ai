CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,  --Using UUIDs makes IDs difficult to guess/enumerate.
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sequence_number INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversations(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_message_role
        CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT'))
);

CREATE INDEX idx_conversations_updated_at
    ON conversations(updated_at);

CREATE INDEX idx_messages_conversation_sequence
    ON messages(conversation_id, sequence_number);