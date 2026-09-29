package com.lamorim.hubdeia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MessageAcceptedResponse(
		String status,

		@JsonProperty("conversation_id")
		String conversationId,

		@JsonProperty("message_id")
		String messageId
) {
}
