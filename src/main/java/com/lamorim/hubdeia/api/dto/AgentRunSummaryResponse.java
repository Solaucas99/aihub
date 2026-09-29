package com.lamorim.hubdeia.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentRunSummaryResponse(
		String id,
		String status,

		@JsonProperty("triggering_message_id")
		String triggeringMessageId,

		@JsonProperty("work_context_id")
		String workContextId,

		@JsonProperty("created_at")
		Instant createdAt,

		@JsonProperty("finished_at")
		Instant finishedAt
) {
}
