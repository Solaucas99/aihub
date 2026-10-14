package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** One attempt of a subagent on a task, including its raw messages and tool calls. */
@Document("task_executions")
@CompoundIndex(def = "{'conversation_id': 1, 'task_id': 1}")
@CompoundIndex(def = "{'conversation_id': 1, 'subagent_id': 1, 'started_at': -1}")
public record SubagentExecution(
		String id,

		@Field("conversation_id")
		String conversationId,

		@Field("agent_run_id")
		String agentRunId,

		@Field("task_id")
		String taskId,

		@Field("subagent_id")
		String subagentId,

		List<ExecutionMessage> messages,

		@Field("tool_calls")
		List<ToolCall> toolCalls,

		Map<String, Object> input,
		Map<String, Object> output,
		String status,
		int attempt,

		@Field("started_at")
		Instant startedAt,

		@Field("finished_at")
		Instant finishedAt
) {

	public SubagentExecution {
		messages = messages == null ? List.of() : messages;
		toolCalls = toolCalls == null ? List.of() : toolCalls;
	}
}
