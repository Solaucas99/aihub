package com.lamorim.hubdeia.application.planner;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.application.ArtifactService;
import com.lamorim.hubdeia.application.ConversationService;
import com.lamorim.hubdeia.application.HumanInputService;
import com.lamorim.hubdeia.application.Text;
import com.lamorim.hubdeia.application.WorkContextService;
import com.lamorim.hubdeia.application.content.TaskOutput;
import com.lamorim.hubdeia.application.run.AgentRunService;
import com.lamorim.hubdeia.domain.model.AgentRun;
import com.lamorim.hubdeia.domain.model.ConversationWorkContext;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;

/** Gathers the conversation state and hands it to the planner model. */
@Component
public class ConversationPlannerAgent implements PlannerAgent {

	private static final int HISTORY_SIZE = 10;
	private static final int RECENT_RUNS = 3;
	private static final int REQUEST_CHARS = 400;
	private static final int RESULT_CHARS = 700;

	private final ConversationService conversations;
	private final WorkContextService contexts;
	private final SubagentRepository subagents;
	private final HumanInputService humanInput;
	private final ArtifactService artifacts;
	private final AgentRunService runs;
	private final OpenAiPlannerClient client;

	public ConversationPlannerAgent(
			ConversationService conversations,
			WorkContextService contexts,
			SubagentRepository subagents,
			HumanInputService humanInput,
			ArtifactService artifacts,
			AgentRunService runs,
			OpenAiPlannerClient client
	) {
		this.conversations = conversations;
		this.contexts = contexts;
		this.subagents = subagents;
		this.humanInput = humanInput;
		this.artifacts = artifacts;
		this.runs = runs;
		this.client = client;
	}

	@Override
	public PlannerDecision plan(String conversationId, String messageId) {
		Message current = conversations.message(conversationId, messageId);
		PlanningInput input = new PlanningInput(
				current.content(),
				conversations.historyBefore(current, HISTORY_SIZE),
				contexts.active(conversationId).map(this::toState).orElse(null),
				contexts.paused(conversationId).stream().map(this::toState).toList(),
				runs.recent(conversationId, RECENT_RUNS).stream().map(this::toSummary).toList(),
				humanInput.pendingForConversation(conversationId),
				contexts.enabledDefinitions().stream().map(this::toView).toList(),
				artifacts.list(conversationId));
		return client.plan(input);
	}

	/** What each specialist was asked and answered, in plain text: the planner decides from facts, not from guesses. */
	private PlanningRunSummary toSummary(AgentRun run) {
		return new PlanningRunSummary(run.id(), run.status().name(), run.tasks().stream().map(task -> new PlanningTaskSummary(
				task.subagentSelected(), task.description(), task.status().name(),
				Text.truncate(String.valueOf(task.input().getOrDefault("enriched_content", "")), REQUEST_CHARS),
				Text.truncate(TaskOutput.from(task.output()).plainText(), RESULT_CHARS))).toList());
	}

	private PlanningContextState toState(ConversationWorkContext context) {
		return new PlanningContextState(context.id(), context.workContextId(), context.lastAgentRunId(), runs.lastMessage(context.lastAgentRunId()));
	}

	private PlanningWorkContextView toView(WorkContext context) {
		List<PlanningSubagentView> agents = context.agentIds().isEmpty() ? List.of()
				: subagents.findAllById(context.agentIds()).stream().filter(subagent -> subagent.enabled()).map(PlanningSubagentView::from).toList();
		return new PlanningWorkContextView(context.id(), context.key(), context.name(), context.description(), context.instructions(), agents);
	}
}
