package com.lamorim.hubdeia.application.planner;

import java.util.List;

import com.lamorim.hubdeia.domain.model.Subagent;

public record PlanningSubagentView(
		String id,
		String name,
		String description,
		List<String> produces,
		List<String> consumes
) {

	public static PlanningSubagentView from(Subagent subagent) {
		return new PlanningSubagentView(subagent.id(), subagent.name(), subagent.description(), subagent.produces(), subagent.consumes());
	}
}
