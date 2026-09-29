package com.lamorim.hubdeia.application.planner;

import java.util.List;

public record PlanningWorkContextView(
		String id,
		String key,
		String name,
		String description,
		String instructions,
		List<PlanningSubagentView> subagents
) {
}
