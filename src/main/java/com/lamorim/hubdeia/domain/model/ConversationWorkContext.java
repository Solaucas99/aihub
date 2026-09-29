package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

public record ConversationWorkContext(
		String id,

		@Field("work_context_id")
		String workContextId,

		String status,

		@Field("last_agent_run_id")
		String lastAgentRunId,

		@Field("created_at")
		Instant createdAt,

		@Field("updated_at")
		Instant updatedAt
) {
}
