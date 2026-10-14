package com.lamorim.hubdeia.application.planner;

/** What the planner knows about a work context instance already used in the conversation. */
public record PlanningContextState(
		String id,
		String workContextId,
		String lastAgentRunId,
		String lastAgentRunMessage
) {
}
