package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.ConversationWorkContextStatus;

public interface ConversationWorkContextRepository extends MongoRepository<ConversationWorkContext, String> {

	List<ConversationWorkContext> findByConversationId(String conversationId);

	Optional<ConversationWorkContext> findFirstByConversationIdAndStatus(String conversationId, ConversationWorkContextStatus status);

	List<ConversationWorkContext> findByConversationIdAndStatus(String conversationId, ConversationWorkContextStatus status);
}
