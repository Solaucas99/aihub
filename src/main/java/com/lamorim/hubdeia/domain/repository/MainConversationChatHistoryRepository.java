package com.lamorim.hubdeia.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;

public interface MainConversationChatHistoryRepository
		extends MongoRepository<MainConversationChatHistory, String> {

	List<MainConversationChatHistory> findByUserIdOrderByUpdatedAtDesc(String userId);

	Optional<MainConversationChatHistory> findByIdAndUserId(String id, String userId);
}
