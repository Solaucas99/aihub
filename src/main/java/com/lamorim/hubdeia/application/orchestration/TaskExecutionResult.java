package com.lamorim.hubdeia.application.orchestration;

import java.util.List;
import java.util.Map;

import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.ToolCall;

public record TaskExecutionResult(
		String taskId,
		String subagentId,
		Map<String, Object> output,
		List<Message> messages,
		List<ProviderSession> providerSessions,
		List<ToolCall> toolCalls
) {
}
