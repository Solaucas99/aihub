package com.lamorim.hubdeia.application;

import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lamorim.hubdeia.application.orchestration.AgentRunOrchestrator;
import com.lamorim.hubdeia.application.planner.PlannerAgent;
import com.lamorim.hubdeia.application.planner.PlannerDecision;

@Component
public class ConversationWorker {

	private static final Logger logger = LoggerFactory.getLogger(ConversationWorker.class);

	private final ConversationService conversations;
	private final PlannerAgent planner;
	private final AgentRunOrchestrator orchestrator;

	public ConversationWorker(ConversationService conversations, PlannerAgent planner, AgentRunOrchestrator orchestrator) {
		this.conversations = conversations;
		this.planner = planner;
		this.orchestrator = orchestrator;
	}

	public void dispatch(String userId, String conversationId, String messageId) {
		CompletableFuture.runAsync(() -> {
			try {
				logger.info("Processing message. conversationId={}, messageId={}", conversationId, messageId);
				PlannerDecision decision = planner.plan(conversations.findConversation(userId, conversationId));
				String runId = conversations.applyPlannerDecision(userId, conversationId, messageId, decision);
				if (runId != null) {
					logger.info("Starting AgentRun. conversationId={}, agentRunId={}", conversationId, runId);
					orchestrator.execute(userId, conversationId, runId);
				}
			} catch (Exception error) {
				logger.error("Message processing failed. conversationId={}, messageId={}", conversationId, messageId, error);
				conversations.appendSystemFailure(userId, conversationId, "Nao foi possivel processar sua mensagem agora.");
			}
		});
	}
}
