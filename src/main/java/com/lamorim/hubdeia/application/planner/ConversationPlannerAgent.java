package com.lamorim.hubdeia.application.planner;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lamorim.hubdeia.domain.model.MainConversationChatHistory;
import com.lamorim.hubdeia.domain.model.Message;
import com.lamorim.hubdeia.domain.model.WorkContext;
import com.lamorim.hubdeia.domain.repository.SubagentRepository;
import com.lamorim.hubdeia.domain.repository.WorkContextRepository;

@Component
public class ConversationPlannerAgent implements PlannerAgent {

	private final WorkContextRepository workContexts;
	private final SubagentRepository subagents;
	private final OpenAiPlannerClient client;

	public ConversationPlannerAgent(
			WorkContextRepository workContexts,
			SubagentRepository subagents,
			OpenAiPlannerClient client
	) {
		this.workContexts = workContexts;
		this.subagents = subagents;
		this.client = client;
	}

	@Override
	public PlannerDecision plan(MainConversationChatHistory conversation) {
		if (!client.isEnabled()) {
			throw new IllegalStateException("OPENAI_API_KEY nao configurada para executar o agente planejador.");
		}
		return client.plan(conversation, latestUserMessage(conversation.messages()), workContexts.findByEnabledTrue().stream()
				.map(this::toView)
				.toList());
	}

	private PlanningWorkContextView toView(WorkContext context) {
		List<PlanningSubagentView> agents = context.agentIds() == null || context.agentIds().isEmpty()
				? List.of()
				: subagents.findAllById(context.agentIds()).stream()
						.filter(subagent -> subagent.enabled())
						.map(PlanningSubagentView::from)
						.toList();
		return new PlanningWorkContextView(context.id(), context.key(), context.name(), context.description(), context.instructions(), agents);
	}

	private String latestUserMessage(List<Message> messages) {
		return messages.reversed().stream()
				.filter(message -> "user".equals(message.role()))
				.findFirst()
				.map(Message::content)
				.orElse("");
	}
}
