package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.AgentRun;

public interface AgentRunRepository extends MongoRepository<AgentRun, String> {

	Optional<AgentRun> findByIdAndConversationId(String id, String conversationId);

	List<AgentRun> findByConversationIdOrderByCreatedAtAsc(String conversationId);

	/** Newest first. */
	List<AgentRun> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable page);

	List<AgentRun> findByConversationIdAndTriggeringMessageIdOrderByCreatedAtAsc(String conversationId, String triggeringMessageId);
}
