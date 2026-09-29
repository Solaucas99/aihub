package com.lamorim.hubdeia.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

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

		List<Message> messages,

		@Field("provider_sessions")
		List<ProviderSession> providerSessions,

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
}
