package com.lamorim.hubdeia.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamorim.hubdeia.domain.model.Message;

public record ConversationDetailResponse(
		String id,

		@JsonProperty("chat_last_message")
		String chatLastMessage,

		List<Message> messages,

		@JsonProperty("created_at")
		Instant createdAt,

		@JsonProperty("updated_at")
		Instant updatedAt
) {
}
