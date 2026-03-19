package com.example.demo.repository;

import com.example.demo.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Returns all messages between two users in chronological order.
     * Fetches conversations in both directions (A→B and B→A).
     */
    @Query("""
        SELECT m FROM Message m
        WHERE (m.sender = :userA AND m.receiver = :userB)
           OR (m.sender = :userB AND m.receiver = :userA)
        ORDER BY m.timestamp ASC
        """)
    List<Message> findConversation(@Param("userA") String userA,
                                   @Param("userB") String userB);

    /**
     * Returns the distinct usernames that :user has ever chatted with.
     * Used to pre-populate the conversation list after login.
     */
    @Query("""
        SELECT DISTINCT
            CASE WHEN m.sender = :user THEN m.receiver ELSE m.sender END
        FROM Message m
        WHERE m.sender = :user OR m.receiver = :user
        """)
    List<String> findChatPartners(@Param("user") String user);
}
