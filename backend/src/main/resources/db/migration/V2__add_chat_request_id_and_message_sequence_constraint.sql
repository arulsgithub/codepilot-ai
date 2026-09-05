ALTER TABLE messages
    ADD COLUMN client_request_id UUID;

CREATE UNIQUE INDEX uq_messages_conversation_sequence
    ON messages (conversation_id, sequence_number);

CREATE UNIQUE INDEX uq_messages_conversation_request_role
    ON messages (conversation_id, client_request_id, role)
    WHERE client_request_id IS NOT NULL;
