package com.lamorim.hubdeia.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConversationSummaryResponse(
		String id,

		@JsonProperty("chat_last_message")
		String chatLastMessage,

		@JsonProperty("created_at")
		Instant createdAt,

		@JsonProperty("updated_at")
		Instant updatedAt
) {
}
