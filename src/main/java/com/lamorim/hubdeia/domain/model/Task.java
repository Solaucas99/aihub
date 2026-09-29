package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

public record Task(
		String id,
		String description,

		@Field("subagent_selected")
		String subagentSelected,

		String status,

		@Field("depends_on")
		List<String> dependsOn,

		Map<String, Object> input,
		Map<String, Object> output,
		List<TaskValidation> validations,
		int attempts,

		@Field("max_attempts")
		int maxAttempts,

		@Field("created_at")
		Instant createdAt,

		@Field("finished_at")
		Instant finishedAt
) {
}
