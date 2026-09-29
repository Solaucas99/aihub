package com.lamorim.hubdeia.application.planner;

import java.util.List;

public record PlannedTask(
		String description,
		String enrichedContent,
		String subagentId,
		List<String> dependsOn
) {

	public List<String> safeDependsOn() {
		return dependsOn == null ? List.of() : dependsOn;
	}
}
