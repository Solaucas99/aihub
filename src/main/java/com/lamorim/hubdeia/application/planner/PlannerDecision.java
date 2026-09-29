package com.lamorim.hubdeia.application.planner;

import java.util.List;

public record PlannerDecision(
		boolean shouldCreateTasks,
		String responseMessage,
		String workContextAction,
		String workContextId,
		String relatedHumanInputRequestId,
		List<PlannedTask> tasks
) {

	public List<PlannedTask> safeTasks() {
		return tasks == null ? List.of() : tasks;
	}
}
