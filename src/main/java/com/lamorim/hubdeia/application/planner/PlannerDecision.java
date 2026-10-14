package com.lamorim.hubdeia.application.planner;

import java.util.List;

public record PlannerDecision(
		boolean shouldCreateTasks,
		String responseMessage,
		WorkContextAction workContextAction,
		String workContextId,
		String relatedHumanInputRequestId,
		String reactivateArtifactId,
		List<PlannedTask> tasks
) {

	public PlannerDecision {
		workContextAction = workContextAction == null ? WorkContextAction.NONE : workContextAction;
		tasks = tasks == null ? List.of() : tasks;
	}

	public boolean hasTasks() {
		return shouldCreateTasks && !tasks.isEmpty();
	}
}
