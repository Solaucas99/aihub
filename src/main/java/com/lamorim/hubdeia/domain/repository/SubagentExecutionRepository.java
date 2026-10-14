package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.SubagentExecution;

public interface SubagentExecutionRepository extends MongoRepository<SubagentExecution, String> {

	Optional<SubagentExecution> findFirstByTaskIdOrderByStartedAtDesc(String taskId);

	List<SubagentExecution> findByConversationIdAndSubagentIdOrderByStartedAtDesc(String conversationId, String subagentId, Pageable page);
}
