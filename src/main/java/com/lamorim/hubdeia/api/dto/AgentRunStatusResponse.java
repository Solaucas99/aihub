package com.lamorim.hubdeia.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamorim.hubdeia.domain.model.FinalAnswerBlock;

public record AgentRunStatusResponse(
		@JsonProperty("conversation_id")
		String conversationId,

		@JsonProperty("agent_run_id")
		String agentRunId,

		String status,

		@JsonProperty("execution_status")
		String executionStatus,

		@JsonProperty("current_message")
		String currentMessage,

		List<AgentRunLogEntry> logs,

		@JsonProperty("final_answer")
		List<FinalAnswerBlock> finalAnswer,

		@JsonProperty("updated_at")
		String updatedAt,

		String source
) {
}
