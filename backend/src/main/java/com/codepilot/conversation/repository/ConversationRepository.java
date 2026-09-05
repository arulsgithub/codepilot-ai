package com.codepilot.conversation.repository;

import com.codepilot.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :conversationId")
    Optional<Conversation> findByIdForUpdate(@Param("conversationId") UUID conversationId);

    List<Conversation> findAllByOrderByUpdatedAtDesc();
}
