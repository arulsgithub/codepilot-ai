package com.codepilot.message.repository;

import com.codepilot.conversation.entity.Conversation;
import com.codepilot.message.entity.Message;
import com.codepilot.message.entity.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationOrderBySequenceNumberAsc(
            Conversation conversation
    );

    Integer countByConversation(Conversation conversation);

    Optional<Message> findByConversationAndClientRequestIdAndRole(
            Conversation conversation,
            UUID clientRequestId,
            MessageRole role
    );

    @Query("select coalesce(max(m.sequenceNumber), 0) from Message m where m.conversation = :conversation")
    Integer findMaxSequenceNumberByConversation(Conversation conversation);
}
