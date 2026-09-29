package com.lamorim.hubdeia.domain.model;

import java.time.Instant;

public record AgentRunLog(
		Instant at,
		String level,
		String message,
		String status,
		String executionStatus
) {
}
