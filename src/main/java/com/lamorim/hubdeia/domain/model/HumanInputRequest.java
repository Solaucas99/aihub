package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

public record HumanInputRequest(
		String id,

		@Field("agent_run_id")
		String agentRunId,

		@Field("task_id")
		String taskId,

		String question,
		String status,

		@Field("answer_message_id")
		String answerMessageId,

		@Field("created_at")
		Instant createdAt,

		@Field("answered_at")
		Instant answeredAt
) {
}
