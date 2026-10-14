package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

public record ToolCall(
		String id,

		@Field("tool_id")
		String toolId,

		@Field("provider_id")
		String providerId,

		Map<String, Object> input,
		Map<String, Object> output,

		@Field("output_text")
		String outputText,
		String status,

		@Field("started_at")
		Instant startedAt,

		@Field("finished_at")
		Instant finishedAt
) {
}
