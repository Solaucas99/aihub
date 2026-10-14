package com.lamorim.hubdeia.domain.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.ProviderSession;

public interface ProviderSessionRepository extends MongoRepository<ProviderSession, String> {

	Optional<ProviderSession> findByConversationIdAndSubagentIdAndProviderId(String conversationId, String subagentId, String providerId);
}
