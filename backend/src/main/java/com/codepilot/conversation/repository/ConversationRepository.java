package com.codepilot.conversation.repository;

import com.codepilot.conversation.entity.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every finder takes a userId.
 *
 * Deliberately NOT offering an unscoped findById: the danger is not the list endpoint, it is
 * lookup by id. A caller supplies the id, so an unscoped findById lets anyone read anyone
 * else's conversation just by guessing or observing a UUID. Making the scoped version the
 * ONLY option means a future endpoint cannot forget the check.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :conversationId and c.userId = :userId")
    Optional<Conversation> findByIdForUpdate(@Param("conversationId") UUID conversationId,
                                             @Param("userId") UUID userId);

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    List<Conversation> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}