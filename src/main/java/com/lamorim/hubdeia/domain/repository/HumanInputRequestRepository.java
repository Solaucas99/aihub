package com.lamorim.hubdeia.domain.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.HumanInputRequestStatus;

public interface HumanInputRequestRepository extends MongoRepository<HumanInputRequest, String> {

	List<HumanInputRequest> findByConversationIdAndStatus(String conversationId, HumanInputRequestStatus status);

	List<HumanInputRequest> findByAgentRunIdAndStatus(String agentRunId, HumanInputRequestStatus status);
}
