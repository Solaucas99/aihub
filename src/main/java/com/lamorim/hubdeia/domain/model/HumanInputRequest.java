package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("human_input_requests")
@CompoundIndex(def = "{'conversation_id': 1, 'status': 1}")
public record HumanInputRequest(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("agent_run_id")
		String agentRunId,

		@Field("task_id")
		String taskId,

		String question,
		HumanInputRequestStatus status,

		@Field("answer_message_id")
		String answerMessageId,

		@Field("created_at")
		Instant createdAt,

		@Field("answered_at")
		Instant answeredAt
) {
}
