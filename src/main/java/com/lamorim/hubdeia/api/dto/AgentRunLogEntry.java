package com.lamorim.hubdeia.api.dto;

public record AgentRunLogEntry(
		String at,
		String level,
		String message
) {
}
