package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

public record AgentRunLog(
		Instant at,
		String level,
		String message,
		AgentRunStatus status,

		@Field("execution_status")
		String executionStatus
) {

	public static AgentRunLog info(AgentRunStatus status, String executionStatus, String message) {
		return new AgentRunLog(Instant.now(), "INFO", message, status, executionStatus);
	}

	public static AgentRunLog error(AgentRunStatus status, String executionStatus, String message) {
		return new AgentRunLog(Instant.now(), "ERROR", message, status, executionStatus);
	}
}
