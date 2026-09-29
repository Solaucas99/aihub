package com.lamorim.hubdeia.application.provider;

import com.lamorim.hubdeia.domain.model.ProviderSession;
import com.lamorim.hubdeia.domain.model.SubagentProvider;

public record ProviderToolContext(
		String conversationId,
	String agentRunId,
		String taskId,
		String subagentId,
		SubagentProvider subagentProvider,
		ProviderSession existingSession,
		ProgressReporter progressReporter
) {
}
