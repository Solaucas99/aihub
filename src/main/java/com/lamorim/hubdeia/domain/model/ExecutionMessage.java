package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

/** Message exchanged between a subagent and its model or tools, stored inside a {@link SubagentExecution}. */
public record ExecutionMessage(
		String id,
		String role,
		String content,

		@Field("created_at")
		Instant createdAt
) {
}
