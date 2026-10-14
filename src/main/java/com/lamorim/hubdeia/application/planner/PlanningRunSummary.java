package com.lamorim.hubdeia.application.planner;

import java.util.List;

/** An earlier agent run of the conversation. */
public record PlanningRunSummary(
		String runId,
		String status,
		List<PlanningTaskSummary> tasks
) {
}
