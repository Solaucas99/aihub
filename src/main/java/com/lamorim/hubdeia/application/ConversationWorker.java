package com.lamorim.hubdeia.application;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import com.lamorim.hubdeia.application.orchestration.AgentRunOrchestrator;
import com.lamorim.hubdeia.application.planner.PlannerAgent;
import com.lamorim.hubdeia.application.planner.PlannerDecision;
import com.lamorim.hubdeia.application.planner.PlannerDecisionHandler;

/** Processes each accepted user message in the background: plan, apply the decision, run the agents. */
@Component
public class ConversationWorker {

	private static final Logger logger = LoggerFactory.getLogger(ConversationWorker.class);

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final ReentrantLock[] planningLocks = Stream.generate(ReentrantLock::new).limit(64).toArray(ReentrantLock[]::new);
	private final ConversationService conversations;
	private final PlannerAgent planner;
	private final PlannerDecisionHandler decisions;
	private final AgentRunOrchestrator orchestrator;

	public ConversationWorker(ConversationService conversations, PlannerAgent planner, PlannerDecisionHandler decisions, AgentRunOrchestrator orchestrator) {
		this.conversations = conversations;
		this.planner = planner;
		this.decisions = decisions;
		this.orchestrator = orchestrator;
	}

	public void dispatch(String conversationId, String messageId) {
		executor.execute(() -> process(conversationId, messageId));
	}

	/**
	 * Messages of one conversation are planned one at a time, in arrival order, so each plan sees the
	 * context left by the previous one. Running the agents is not serialized: the user can keep chatting.
	 */
	private Optional<String> planAndApply(String conversationId, String messageId) {
		ReentrantLock lock = planningLocks[Math.floorMod(conversationId.hashCode(), planningLocks.length)];
		lock.lock();
		try {
			PlannerDecision decision = planner.plan(conversationId, messageId);
			return decisions.apply(conversationId, messageId, decision);
		} finally {
			lock.unlock();
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}

	private void process(String conversationId, String messageId) {
		try {
			logger.info("Processing message. conversationId={}, messageId={}", conversationId, messageId);
			planAndApply(conversationId, messageId).ifPresent(runId -> {
				logger.info("Starting AgentRun. conversationId={}, agentRunId={}", conversationId, runId);
				orchestrator.execute(conversationId, runId);
			});
		} catch (Exception error) {
			logger.error("Message processing failed. conversationId={}, messageId={}", conversationId, messageId, error);
			conversations.appendAssistantMessage(conversationId, "Nao foi possivel processar sua mensagem agora.");
		}
	}
}
