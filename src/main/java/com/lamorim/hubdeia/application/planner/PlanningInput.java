package com.lamorim.hubdeia.application.planner;

import java.util.List;

import com.lamorim.hubdeia.application.ArtifactView;
import com.lamorim.hubdeia.domain.model.HumanInputRequest;
import com.lamorim.hubdeia.domain.model.Message;

/** Everything the planner model needs to decide how to handle one user message. */
public record PlanningInput(
		String currentRequest,
		List<Message> history,
		PlanningContextState activeContext,
		List<PlanningContextState> pausedContexts,
		List<PlanningRunSummary> recentRuns,
		List<HumanInputRequest> pendingRequests,
		List<PlanningWorkContextView> availableContexts,
		List<ArtifactView> artifacts
) {
}
