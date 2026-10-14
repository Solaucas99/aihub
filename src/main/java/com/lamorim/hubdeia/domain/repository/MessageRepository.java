package com.lamorim.hubdeia.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

	List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

	Optional<Message> findByIdAndConversationId(String id, String conversationId);

	/** Newest first; callers reverse when they need chronological order. */
	List<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable page);

	List<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(String conversationId, Instant before, Pageable page);
}
