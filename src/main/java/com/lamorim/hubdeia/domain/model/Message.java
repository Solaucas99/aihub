package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Chat message of a conversation. */
@Document("messages")
@CompoundIndex(def = "{'conversation_id': 1, 'created_at': 1}")
public record Message(
		String id,

		@JsonIgnore
		@Field("conversation_id")
		String conversationId,

		String role,
		String content,

		@Field("created_at")
		Instant createdAt
) {
}
