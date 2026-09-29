package com.lamorim.hubdeia.application.planner;

import com.lamorim.hubdeia.domain.model.Subagent;

public record PlanningSubagentView(
		String id,
		String name,
		String description
) {

	public static PlanningSubagentView from(Subagent subagent) {
		return new PlanningSubagentView(
				subagent.id(),
				subagent.name(),
			subagent.description()
		);
	}
}
