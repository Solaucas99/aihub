package com.lamorim.hubdeia.application.planner;

/** One task of an earlier run, as the planner sees it: who did it, what they were asked and what came back. */
public record PlanningTaskSummary(
		String subagentId,
		String description,
		String status,
		String request,
		String result
) {
}
