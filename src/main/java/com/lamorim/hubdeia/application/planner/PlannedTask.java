package com.lamorim.hubdeia.application.planner;

public record PlannedTask(
		String description,
		String enrichedContent,
		String subagentId
) {
}
