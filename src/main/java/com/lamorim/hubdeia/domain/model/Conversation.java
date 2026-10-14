package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("conversations")
@CompoundIndex(def = "{'user_id': 1, 'updated_at': -1}")
public record Conversation(
		@Id
		String id,

		@Field("user_id")
		String userId,

		@Field("chat_last_message")
		String chatLastMessage,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {
}
