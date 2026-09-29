package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

public record AgentRun(
		String id,
		String status,
		List<Task> tasks,
		List<AgentRunLog> logs,

		@Field("final_answer")
		List<FinalAnswerBlock> finalAnswer,

		@Field("triggering_message_id")
		String triggeringMessageId,

		@Field("conversation_work_context_id")
		String conversationWorkContextId,

		@Field("continued_human_input_request_id")
		String continuedHumanInputRequestId,

		@Field("agent_run_last_message")
		String agentRunLastMessage,

		@Field("created_at")
		Instant createdAt,

		@Field("finished_at")
		Instant finishedAt
) {
}
