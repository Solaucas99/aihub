package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.Conversation;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

	List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);

	Optional<Conversation> findByIdAndUserId(String id, String userId);
}
