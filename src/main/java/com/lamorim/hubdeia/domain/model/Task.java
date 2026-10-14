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

		TaskStatus status,
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

	public Task {
		input = input == null ? Map.of() : input;
		output = output == null ? Map.of() : output;
		validations = validations == null ? List.of() : validations;
	}

	public static Task create(String id, String description, String subagentId, String enrichedContent, int maxAttempts, Instant now) {
		return new Task(id, description, subagentId, TaskStatus.NEW, Map.of("enriched_content", enrichedContent == null ? "" : enrichedContent),
				Map.of(), List.of(), 0, maxAttempts, now, null);
	}

	public boolean isApproved() {
		return !validations.isEmpty() && "CONTINUE".equals(validations.getLast().status());
	}
}
