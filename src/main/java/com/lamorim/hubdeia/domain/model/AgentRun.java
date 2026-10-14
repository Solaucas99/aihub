package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("agent_runs")
@CompoundIndex(def = "{'conversation_id': 1, 'created_at': 1}")
public record AgentRun(
		@Id
		String id,

		@Field("conversation_id")
		String conversationId,

		AgentRunStatus status,
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

	public AgentRun {
		tasks = tasks == null ? List.of() : tasks;
		logs = logs == null ? List.of() : logs;
		finalAnswer = finalAnswer == null ? List.of() : finalAnswer;
	}
}
